/*
 * 曲奇小镇前端脚本（原生 JS，无构建、无依赖）
 *
 * 职责：
 *  1) 会话管理：登录存 token/user 到 localStorage，GET /api/auth/me 校验刷新，登出清除。
 *  2) 通用请求接线：表单按 data-endpoint / data-method 自动提交；
 *     - 端点中的 {param} 用同名字段值替换（路径参数），并从请求体移除；
 *     - data-query-params 列出的字段拼到查询串（对应后端 @RequestParam）；
 *     - data-number-fields 列出的字段转为数字（对应坐标等 int 字段）；
 *     - 其余字段作为 JSON 请求体（对应后端 @RequestBody）。
 *  3) 交互磁贴：各 .tile-grid[data-endpoint] 拉取数据渲染成磁贴，
 *     每个磁贴带编辑/删除按钮与可展开（<details>）的子资源面板，覆盖后端全部端点。
 *  4) 权限门控：写操作默认需 Common+，data-require-permission 可覆盖（如公告=Admin）；
 *     权限不足则禁用提交/按钮。属主校验由后端负责，越权时回显 403 文案。
 */
(function () {
    'use strict';

    // 所有 endpoint（data-endpoint / TILE_BUILDERS 键 / 会话校验）均为以 /api 开头的绝对路径，
    // apiFetch 不再额外拼接前缀，避免出现 /api/api/... 的双重前缀。
    const TOKEN_KEY = 'ct_token';
    const USER_KEY = 'ct_user';
    // 权限等级：Admin > Common > Visitor/Marked（后两者只读）
    const PERMISSION_RANK = {Visitor: 0, Marked: 0, Common: 1, Admin: 2};

    // 特权标签白名单（启动时从 GET /api/config/privileged-tags 拉取）：
    // 拥有其中任一标签的用户视为「开发者」，可修复其它玩家账户信息（用户名/简介/重置密码）
    let PRIVILEGED_TAGS = [];

    // 枚举选项（与后端 entitys.enums 对齐）
    const SCORES = [
        {value: 'Perfect', label: '极好（5）'}, {value: 'Good', label: '好（4）'},
        {value: 'Average', label: '一般（3）'}, {value: 'Bad', label: '差（2）'},
        {value: 'Terrible', label: '极差（1）'}
    ];
    const LANDMARK_TYPES = [
        {value: 'Building', label: '普通建筑'}, {value: 'Home', label: '家'},
        {value: 'ResourceCreate', label: '资源刷取点'}, {value: 'ViewPoint', label: '观光点'}
    ];
    const LANDMARK_STATUS = [
        {value: 'Normal', label: '正常'}, {value: 'Maintaining', label: '维护中'},
        {value: 'Abandoned', label: '废弃'}
    ];
    const PERMISSIONS = [
        {value: 'Admin', label: '管理员'}, {value: 'Common', label: '成员'},
        {value: 'Visitor', label: '访客'}, {value: 'Marked', label: '受限'}
    ];

    const DEFAULT_AVATAR = 'data:image/svg+xml;utf8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">' +
        '<rect width="64" height="64" rx="8" fill="#c9ccd6"/>' +
        '<circle cx="32" cy="24" r="11" fill="#82879a"/>' +
        '<path d="M10 58c0-12 10-18 22-18s22 6 22 18z" fill="#82879a"/>' +
        '</svg>');

    // 取用户头像 URL：无则回退默认头像
    function avatarSrc(user) {
        return (user && user.avatar) ? user.avatar : DEFAULT_AVATAR;
    }

    // ---------------- 会话 ----------------
    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function getCurrentUser() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
        } catch (e) {
            return null;
        }
    }

    function setSession(token, user) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
    }

    function clearSession() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }

    function hasPermission(userPerm, required) {
        if (!required) return true;
        const have = PERMISSION_RANK[userPerm];
        const need = PERMISSION_RANK[required];
        return (have === undefined ? -1 : have) >= (need === undefined ? 99 : need);
    }

    // 当前登录用户是否满足某权限要求（未登录视为不满足）
    function can(required) {
        const user = getCurrentUser();
        return !!(user && hasPermission(user.permission, required));
    }

    // 拉取特权标签白名单（失败则视为空，前端不放行；后端始终是权威）
    async function loadPrivilegedTags() {
        try {
            const tags = await apiFetch('/api/config/privileged-tags');
            PRIVILEGED_TAGS = Array.isArray(tags) ? tags : [];
        } catch (e) {
            PRIVILEGED_TAGS = [];
        }
    }

    // 某用户是否为开发者（拥有任一特权标签）
    function isDeveloper(user) {
        if (!user || !Array.isArray(user.tags) || PRIVILEGED_TAGS.length === 0) return false;
        return user.tags.some(function (t) {
            return PRIVILEGED_TAGS.indexOf(t) !== -1;
        });
    }

    // 当前登录用户能否管理目标用户的账户信息（本人或开发者；管理员角色不再自动放行）
    function canManageAccount(targetUuid) {
        const me = getCurrentUser();
        return !!(me && (me.uuid === targetUuid || isDeveloper(me)));
    }

    // ---------------- API 封装 ----------------
    async function apiFetch(path, options) {
        options = options || {};
        const method = (options.method || 'GET').toUpperCase();
        const headers = {};
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        let body;
        if (options.body !== undefined) {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(options.body);
        }

        let resp;
        try {
            resp = await fetch(path, {method: method, headers: headers, body: body});
        } catch (e) {
            throw new Error('网络错误：无法连接服务器');
        }

        if (resp.status === 204) return null;

        const text = await resp.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = {error: text};
            }
        }

        if (!resp.ok) {
            if (resp.status === 401) clearSession();
            const msg = (data && data.error) ? data.error : ('请求失败（HTTP ' + resp.status + '）');
            const err = new Error(msg);
            err.status = resp.status;
            throw err;
        }
        return data;
    }

    async function apiUpload(url, file, fieldName) {
        const fd = new FormData();
        fd.append(fieldName || 'file', file);
        const headers = {};
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        let resp;
        try {
            resp = await fetch(url, {method: 'POST', headers: headers, body: fd});
        } catch (e) {
            throw new Error('网络错误：无法连接服务器');
        }
        const text = await resp.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = {error: text};
            }
        }
        if (!resp.ok) {
            if (resp.status === 401) clearSession();
            const msg = (data && data.error) ? data.error : ('上传失败（HTTP ' + resp.status + '）');
            const err = new Error(msg);
            err.status = resp.status;
            throw err;
        }
        return data;
    }

    // ---------------- 小工具 ----------------
    function fmtTime(ms) {
        if (!ms) return '';
        return new Date(ms).toLocaleString();
    }

    // 总评分显示：数字保留 1 位小数（文档「总评分，double，保留 1 位小数」）；非数字原样返回
    function fmtScore(n) {
        if (typeof n === 'number' && !isNaN(n)) return n.toFixed(1);
        return (n == null) ? '' : String(n);
    }

    // Score 枚举值 -> 友好标签（Perfect -> 极好（5））；空值返回 ''（磁贴会跳过不显示）
    function scoreLabel(v) {
        if (v == null || v === '') return '';
        const hit = SCORES.filter(function (o) {
            return o.value === v;
        })[0];
        return hit ? hit.label : String(v);
    }

    // 权限 -> CSS 类名（perm-admin / perm-common / perm-visitor / perm-marked），供着色
    function permClass(p) {
        return 'perm-' + String(p == null ? '' : p).toLowerCase();
    }

    // 权限枚举 -> 友好标签（Admin -> 管理员）
    function permLabel(p) {
        const hit = PERMISSIONS.filter(function (o) {
            return o.value === p;
        })[0];
        return hit ? hit.label : String(p == null ? '' : p);
    }

    function el(tag, className, text) {
        const n = document.createElement(tag);
        if (className) n.className = className;
        if (text !== undefined && text !== null) n.textContent = text;
        return n;
    }

    let formSeq = 0;

    // ---------------- 会话状态区 ----------------
    function renderSessionStatus() {
        const box = document.getElementById('session-status');
        const user = getCurrentUser();
        if (box) {
            box.textContent = '';
            if (user) {
                const av = document.createElement('img');
                av.src = avatarSrc(user);
                av.alt = user.name;
                av.className = 'session-avatar';
                box.appendChild(av);
                box.appendChild(el('span', 'session-name', user.name + '（' + user.permission + '）'));
                const logout = el('button', 'session-logout', '登出');
                logout.type = 'button';
                logout.addEventListener('click', function () {
                    clearSession();
                    location.reload();
                });
                box.appendChild(logout);
            } else {
                box.textContent = '未登录';
            }
        }
        document.querySelectorAll('a[href="login.html"], a[href="register.html"]').forEach(function (a) {
            a.hidden = !!user;
        });
        document.body.classList.toggle('logged-in', !!user);
    }

    async function refreshSession() {
        if (!getToken()) {
            renderSessionStatus();
            return null;
        }
        try {
            const me = await apiFetch('/api/auth/me');
            localStorage.setItem(USER_KEY, JSON.stringify(me));
            renderSessionStatus();
            return me;
        } catch (e) {
            clearSession();
            renderSessionStatus();
            return null;
        }
    }

    // ---------------- 表单/请求解析 ----------------
    function resolveEndpoint(form) {
        const fd = new FormData(form);
        const body = {};
        const fileFields = (form.dataset.fileFields || '').split(',').map(function (s) {
            return s.trim();
        }).filter(Boolean);
        fd.forEach(function (v, k) {
            // 文件字段不进 JSON body（改由 multipart 上传）
            if (fileFields.indexOf(k) !== -1) return;
            // 可选字段留空则不提交（如 parentTownUuid / parentUuid）
            if (typeof v === 'string' && v.trim() === '') return;
            body[k] = v;
        });
        const missing = [];
        let url = form.dataset.endpoint || '';

        // 1) 路径参数：{name} 用同名字段替换并移出 body
        url = url.replace(/\{(\w+)}/g, function (_, name) {
            if (body[name] !== undefined) {
                const val = body[name];
                delete body[name];
                return encodeURIComponent(val);
            }
            missing.push(name);
            return '';
        });

        // 2) 查询参数：data-query-params 列出的字段拼到 ?k=v 并移出 body
        const qp = (form.dataset.queryParams || '').split(',').map(function (s) {
            return s.trim();
        }).filter(Boolean);
        if (qp.length) {
            const parts = [];
            qp.forEach(function (name) {
                if (body[name] !== undefined) {
                    parts.push(encodeURIComponent(name) + '=' + encodeURIComponent(body[name]));
                    delete body[name];
                } else {
                    missing.push(name);
                }
            });
            if (parts.length) url += (url.indexOf('?') === -1 ? '?' : '&') + parts.join('&');
        }

        // 3) 数字字段：data-number-fields 列出的字段转为 Number（坐标 x/y/z/level）
        const nf = (form.dataset.numberFields || '').split(',').map(function (s) {
            return s.trim();
        }).filter(Boolean);
        nf.forEach(function (name) {
            if (body[name] !== undefined) {
                const n = Number(body[name]);
                body[name] = isNaN(n) ? body[name] : n;
            }
        });

        // 4) 列表字段：data-list-fields 列出的字段按换行拆成字符串数组（如图片 URL 列表）
        const lf = (form.dataset.listFields || '').split(',').map(function (s) {
            return s.trim();
        }).filter(Boolean);
        lf.forEach(function (name) {
            if (body[name] !== undefined) {
                body[name] = String(body[name]).split('\n')
                    .map(function (s) {
                        return s.trim();
                    })
                    .filter(Boolean);
            }
        });

        return {url: url, body: body, missing: missing};
    }

    function showMsg(form, text, isError) {
        const m = form.querySelector('.form-message');
        if (!m) return;
        m.textContent = text || '';
        m.classList.toggle('error', !!isError);
        m.classList.toggle('success', !isError && !!text);
    }

    // 收集表单中 data-file-fields 指定的文件输入里的文件
    function collectFiles(form) {
        const names = (form.dataset.fileFields || '').split(',').map(function (s) {
            return s.trim();
        }).filter(Boolean);
        const files = [];
        names.forEach(function (name) {
            const inp = form.querySelector('input[type="file"][name="' + name + '"]');
            if (inp && inp.files) {
                Array.prototype.forEach.call(inp.files, function (f) {
                    files.push(f);
                });
            }
        });
        return files;
    }

    function wireForm(form) {
        if (form.dataset.wired === '1') return;
        form.dataset.wired = '1';
        form.addEventListener('submit', async function (ev) {
            ev.preventDefault();
            const method = (form.dataset.method || 'POST').toUpperCase();
            const resolved = resolveEndpoint(form);
            if (resolved.missing.length) {
                showMsg(form, '缺少参数：' + resolved.missing.join(', '), true);
                return;
            }
            const files = collectFiles(form);
            showMsg(form, '提交中…', false);
            try {
                const hasBody = Object.keys(resolved.body).length > 0;
                const data = await apiFetch(resolved.url, {method: method, body: hasBody ? resolved.body : undefined});
                // 两步上传：主请求成功后，把选中的本地图片上传到 data-upload-endpoint（{uuid} 用响应的 uuid 替换）
                const uploadEp = form.dataset.uploadEndpoint;
                if (uploadEp && files.length) {
                    const uid = (data && data.uuid) || resolved.body.uuid || '';
                    const target = uploadEp.replace('{uuid}', encodeURIComponent(uid));
                    showMsg(form, '上传图片中…', false);
                    for (let i = 0; i < files.length; i++) {
                        await apiUpload(target, files[i], 'file');
                    }
                }
                handleFormSuccess(form, data);
            } catch (e) {
                showMsg(form, e.message, true);
                if (e.status === 401) renderSessionStatus();
            }
        });
    }

    function handleFormSuccess(form, data) {
        const id = form.id || '';
        if (id === 'login-form' && data && data.token) {
            setSession(data.token, data.user);
            showMsg(form, '登录成功，正在跳转…', false);
            renderSessionStatus();
            setTimeout(function () {
                location.href = 'index.html';
            }, 600);
            return;
        }
        if (id === 'register-form') {
            showMsg(form, '注册成功，请登录（新账号默认为访客，需联系管理员）。', false);
            form.reset();
            setTimeout(function () {
                location.href = 'login.html';
            }, 1200);
            return;
        }
        showMsg(form, '操作成功。', false);
        form.reset();
        prefillSelfUuid(form);
        loadAllLists();
        applyPermissionGating();
        // 提交成功自动收起面板
        const panel = form.closest('details');
        if (panel) setTimeout(function () {
            panel.open = false;
        }, 1200);
    }

    // 用户改资料表单：{uuid} 自动填当前登录用户
    function prefillSelfUuid(form) {
        const ep = form.dataset.endpoint || '';
        if (ep.indexOf('{uuid}') === -1) return;
        const uuidField = form.querySelector('[name="uuid"]');
        const user = getCurrentUser();
        if (uuidField && user && !uuidField.value) uuidField.value = user.uuid;

    }

    // ---------------- 磁贴构件 ----------------
    // 只读信息磁贴
    function tile(uuid, rows) {
        const t = el('div', 'tile tile-item');
        t.dataset.uuid = uuid || '';
        rows.forEach(function (pair) {
            const k = pair[0], v = pair[1];
            if (v === undefined || v === null || v === '') return;
            const line = el('div', 'tile-row');
            line.appendChild(el('span', 'tile-key', k + '：'));
            line.appendChild(el('span', 'tile-val', String(v)));
            t.appendChild(line);
        });
        return t;
    }

    // 由字段描述生成一个表单控件（text/textarea/select/hidden/number…）
    function fieldToInput(f, idBase) {
        const wrap = el('div', 'tile-field');
        const id = idBase + '-' + f.name;
        let input;
        if (f.type === 'textarea') {
            input = document.createElement('textarea');
            input.rows = f.rows || 3;
            input.value = (f.value == null ? '' : f.value);
        } else if (f.type === 'select') {
            input = document.createElement('select');
            (f.options || []).forEach(function (o) {
                const op = document.createElement('option');
                op.value = o.value;
                op.textContent = o.label;
                if (String(f.value) === String(o.value)) op.selected = true;
                input.appendChild(op);
            });
        } else {
            input = document.createElement('input');
            input.type = f.type || 'text';
            input.value = (f.value == null ? '' : f.value);
            if (f.placeholder) input.placeholder = f.placeholder;
            if (f.minlength) input.minLength = f.minlength;
            if (f.maxlength) input.maxLength = f.maxlength;
        }
        input.id = id;
        input.name = f.name;
        if (f.required) input.required = true;
        if (f.type !== 'hidden') {
            const label = document.createElement('label');
            label.htmlFor = id;
            label.textContent = f.label;
            wrap.appendChild(label);
        }
        wrap.appendChild(input);
        if (f.hint) wrap.appendChild(el('small', 'field-hint', f.hint));
        return wrap;
    }

    // 由配置构建一个可提交的磁贴表单，并按权限门控
    function buildForm(cfg) {
        const form = el('form', 'tile-form tile-form-inline');
        form.action = '#';
        form.method = (cfg.method || 'POST').toLowerCase();
        form.dataset.endpoint = cfg.endpoint;
        form.dataset.method = (cfg.method || 'POST').toUpperCase();

        if (cfg.query && cfg.query.length) form.dataset.queryParams = cfg.query.join(',');
        if (cfg.numbers && cfg.numbers.length) form.dataset.numberFields = cfg.numbers.join(',');
        if (cfg.lists && cfg.lists.length) form.dataset.listFields = cfg.lists.join(',');
        if (cfg.requirePermission) form.dataset.requirePermission = cfg.requirePermission;

        const idBase = 'f' + (formSeq++);
        (cfg.fields || []).forEach(function (f) {
            form.appendChild(fieldToInput(f, idBase));
        });

        const actions = el('div', 'tile-actions');
        const btn = el('button', 'tile-action', cfg.submitLabel || '提交');
        btn.type = 'submit';
        actions.appendChild(btn);
        form.appendChild(actions);
        form.appendChild(el('p', 'form-message', ''));

        // 权限门控：不足则禁用
        if (cfg.requirePermission && !can(cfg.requirePermission)) {
            btn.disabled = true;
            form.classList.add('disabled');
            showMsg(form, can0Hint(cfg.requirePermission), true);
        }
        wireForm(form);
        return form;
    }

    function can0Hint(required) {
        const user = getCurrentUser();
        return user
            ? ('当前权限（' + user.permission + '）不足，需要 ' + required + '。')
            : '请先登录（并具备相应权限）后再操作。';
    }

    // 删除按钮（带确认），属主校验由后端负责
    function buildDeleteButton(endpoint, label, requirePermission) {
        const btn = el('button', 'tile-action tile-action-danger', label || '删除');
        btn.type = 'button';
        if (requirePermission && !can(requirePermission)) {
            btn.disabled = true;
            btn.title = can0Hint(requirePermission);
        }
        btn.addEventListener('click', async function () {
            if (!confirm('确定执行「' + (label || '删除') + '」？此操作不可撤销。')) return;
            btn.disabled = true;
            try {
                await apiFetch(endpoint, {method: 'DELETE'});
                loadAllLists();
            } catch (e) {
                alert('操作失败：' + e.message);
                btn.disabled = false;
                if (e.status === 401) renderSessionStatus();
            }
        });
        return btn;
    }

    // 可复用的文件上传控件：选择本地图片 -> multipart POST -> 重新加载列表
    const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    function buildUploadControl(cfg) {
        const wrap = el('div', 'tile-upload');
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = cfg.accept || 'image/*';
        if (cfg.multiple) input.multiple = true;

        const btn = el('button', 'tile-action', cfg.label || '上传图片');
        btn.type = 'button';
        if (cfg.requirePermission && !can(cfg.requirePermission)) {
            btn.disabled = true;
            btn.title = can0Hint(cfg.requirePermission);
        }
        const msg = el('p', 'form-message', '');
        btn.addEventListener('click', async function () {
            const files = Array.prototype.slice.call(input.files || []);
            if (!files.length) {
                msg.textContent = '请先选择文件';
                msg.className = 'form-message error';
                return;
            }
            // 客户端预校验：过大文件直接拒绝并清空，避免上传后被卡在输入框里删不掉
            const oversized = files.filter(function (f) {
                return f.size > MAX_UPLOAD_BYTES;
            });
            if (oversized.length) {
                msg.textContent = '以下图片超过 5MB，已清空选择：' + oversized.map(function (f) {
                    return f.name;
                }).join('、');
                msg.className = 'form-message error';
                input.value = '';
                return;
            }
            btn.disabled = true;
            msg.textContent = '上传中…';
            msg.className = 'form-message';
            try {
                for (let i = 0; i < files.length; i++) {
                    await apiUpload(cfg.endpoint, files[i], cfg.fieldName || 'file');
                }
                input.value = '';
                if (cfg.onSuccess) await cfg.onSuccess();
                loadAllLists();
            } catch (e) {
                msg.textContent = e.message;
                msg.className = 'form-message error';
                input.value = '';
                btn.disabled = false;
                if (e.status === 401) renderSessionStatus();
            }
        });
        wrap.appendChild(input);
        wrap.appendChild(btn);
        wrap.appendChild(msg);
        return wrap;
    }

    // 把节点包进一个可折叠的 <details>
    function wrapInDetails(title, node) {
        const d = el('details', 'tile-subform');
        d.appendChild(el('summary', null, title));
        d.appendChild(node);
        return d;
    }

    // 子资源面板：展开时懒加载列表 + 常驻的增删表单
    function buildPanel(p) {
        const det = el('details', 'tile-panel');
        det.appendChild(el('summary', null, p.title));
        const listBox = el('div', 'tile-panel-list');
        const formsBox = el('div', 'tile-panel-forms');
        det.appendChild(listBox);
        det.appendChild(formsBox);
        (p.forms || []).forEach(function (cfg) {
            formsBox.appendChild(wrapInDetails(cfg.title, buildForm(cfg)));
        });
        (p.uploads || []).forEach(function (cfg) {
            formsBox.appendChild(wrapInDetails(cfg.title, buildUploadControl(cfg)));
        });
        let loaded = false;
        det.addEventListener('toggle', async function () {
            if (det.open && !loaded) {
                loaded = true;
                await renderPanelList(p, listBox);
            }
        });
        return det;
    }

    async function renderPanelList(p, box) {
        if (!p.load) {
            box.remove();
            return;
        }
        box.textContent = '加载中…';
        try {
            const data = await apiFetch(p.load.endpoint);
            box.textContent = '';
            if (!Array.isArray(data) || data.length === 0) {
                box.appendChild(el('p', 'empty-hint', '暂无数据。'));
                return;
            }
            data.forEach(function (item) {
                box.appendChild(p.load.render(item));
            });
        } catch (e) {
            box.textContent = '';
            box.appendChild(el('p', 'empty-hint error', '加载失败：' + e.message));
        }
    }

    // 子资源项渲染器
    // 用户磁贴头部：头像 + 用户名 + 内联标签 + 权限徽章 +「我」标识
    function userHead(u) {
        const head = el('div', 'user-head');
        const av = document.createElement('img');
        av.src = avatarSrc(u);
        av.alt = u.name;
        av.className = 'tile-avatar';
        head.appendChild(av);

        const text = el('div', 'user-head-text');
        const line = el('div', 'user-name-line');
        line.appendChild(el('span', 'user-name', u.name));
        // 标签直接跟在用户名后面（标签不会很长）
        (u.tags || []).forEach(function (t) {
            if (t) line.appendChild(el('span', 'user-tag', String(t)));
        });
        // 权限徽章（按权限着色）
        line.appendChild(el('span', 'perm-badge ' + permClass(u.permission), permLabel(u.permission)));
        // 已登录用户主体标识
        const me = getCurrentUser();
        if (me && me.uuid === u.uuid) line.appendChild(el('span', 'me-badge', '我'));
        text.appendChild(line);
        head.appendChild(text);
        return head;
    }

    function userChip(u) {
        const node = tile(u.uuid, [['UUID', u.uuid]]);
        node.classList.add(permClass(u.permission));
        const me = getCurrentUser();
        if (me && me.uuid === u.uuid) node.classList.add('is-me');
        node.insertBefore(userHead(u), node.firstChild);
        return node;
    }

    function coordRow(c) {
        return tile(null, [['坐标', '(' + c.x + ', ' + c.y + ', ' + c.z + ')'], ['维度', c.level]]);
    }

    function textRow(s) {
        const d = el('div', 'tile tile-item');
        d.appendChild(el('span', 'tile-val', String(s)));
        return d;
    }

    // 标签面板行：以 chip 形式展示单个标签
    function tagRow(t) {
        const d = el('div', 'tile tile-item');
        d.appendChild(el('span', 'user-tag', String(t)));
        return d;
    }

    function townChip(t) {
        return tile(t.uuid, [['名称', t.name], ['UUID', t.uuid]]);
    }

    function landmarkChip(l) {
        return tile(l.uuid, [['地标', l.name], ['类型', l.type], ['UUID', l.uuid]]);
    }

    function commentChip(c) {
        return tile(c.uuid, [['内容', c.content], ['发布者', c.publisherUuid], ['UUID', c.uuid]]);
    }

    // 图片行：缩略图 + URL + 移除按钮（移除走 DELETE /pictures?url=）
    function pictureRow(url, ownerUuid, basePath, altText) {
        basePath = basePath || '/api/landmarks';
        const d = el('div', 'tile tile-item tile-picture');
        const a = document.createElement('a');
        a.href = url;
        a.target = '_blank';
        a.rel = 'noopener';
        a.title = url;
        const img = document.createElement('img');
        img.src = url;
        img.alt = altText || '图片';
        img.className = 'picture-thumb';
        a.appendChild(img);
        d.appendChild(a);
        d.appendChild(buildDeleteButton(
            basePath + '/' + ownerUuid + '/pictures?url=' + encodeURIComponent(url),
            '移除', 'Common'));
        return d;
    }

    // ---------------- 各模块交互磁贴构建器 ----------------
    // 镇长显示：用户名 + uuid 组合；无主镇时显示“无”
    function fmtOwner(ownerName, ownerUuid) {
        if (!ownerUuid) return '无';
        return ownerName ? ownerName + '（' + ownerUuid + '）' : ownerUuid;
    }

    function fmtOwnerNoUuid(ownerName, ownerUuid) {
        if (!ownerUuid) return '无';
        return ownerName ? ownerName : ownerUuid + '（已注销）';
    }

    const TILE_BUILDERS = {
        '/api/towns': function (t) {
            const node = tile(t.uuid, [
                ['名称', t.name], ['简介', t.description], ['评分', fmtScore(t.score)],
                ['镇长', fmtOwnerNoUuid(t.ownerName, t.ownerUuid)],
                ['创建', fmtTime(t.createTime)]
            ]);
            const actions = el('div', 'tile-actions');
            actions.appendChild(wrapInDetails('编辑', buildForm({
                title: '编辑',
                endpoint: '/api/towns/' + t.uuid,
                method: 'PUT',
                submitLabel: '保存',
                requirePermission: 'Common',
                fields: [
                    {name: 'name', label: '名称', value: t.name, type: 'text', required: true},
                    {name: 'description', label: '简介', value: t.description, type: 'textarea'}
                ]
            })));
            actions.appendChild(buildDeleteButton('/api/towns/' + t.uuid, '删除', 'Common'));
            node.appendChild(actions);
            node.appendChild(buildPanel({
                title: '成员', load: {endpoint: '/api/towns/' + t.uuid + '/members', render: userChip},
                forms: [
                    {
                        title: '添加成员',
                        endpoint: '/api/towns/' + t.uuid + '/members/{userUuid}',
                        method: 'POST',
                        submitLabel: '添加',
                        requirePermission: 'Common',
                        fields: [{name: 'userUuid', label: '用户 UUID', type: 'text', required: true}]
                    },
                    {
                        title: '移除成员',
                        endpoint: '/api/towns/' + t.uuid + '/members/{userUuid}',
                        method: 'DELETE',
                        submitLabel: '移除',
                        requirePermission: 'Common',
                        fields: [{name: 'userUuid', label: '用户 UUID', type: 'text', required: true}]
                    }
                ]
            }));
            node.appendChild(buildPanel({
                title: '子城镇',
                load: {endpoint: '/api/towns/' + t.uuid + '/children', render: townChip}
            }));
            node.appendChild(buildPanel({
                title: '图片',
                load: {
                    endpoint: '/api/towns/' + t.uuid + '/pictures',
                    render: function (url) {
                        return pictureRow(url, t.uuid, '/api/towns', '小镇图片');
                    }
                },
                uploads: [{
                    title: '上传图片',
                    endpoint: '/api/towns/' + t.uuid + '/pictures/upload',
                    label: '上传',
                    multiple: true,
                    requirePermission: 'Common'
                }]
            }));
            return node;
        },

        '/api/landmarks': function (l) {
            const node = tile(l.uuid, [
                ['地标', l.name], ['类型', l.type], ['状态', l.status],
                ['简介', l.description], ['评分', fmtScore(l.score)],
                ['提交者', fmtOwnerNoUuid(l.submitterName, l.submitterUuid)]
            ]);
            const actions = el('div', 'tile-actions');
            const detail = el('a', 'tile-action tile-detail-link', '详情页');
            detail.href = 'landmark.html?uuid=' + encodeURIComponent(l.uuid);
            actions.appendChild(detail);
            actions.appendChild(wrapInDetails('编辑', buildForm({
                title: '编辑',
                endpoint: '/api/landmarks/' + l.uuid,
                method: 'PUT',
                submitLabel: '保存',
                requirePermission: 'Common',
                fields: [
                    {name: 'name', label: '地标名', value: l.name, type: 'text', required: true},
                    {name: 'type', label: '类型', value: l.type, type: 'select', options: LANDMARK_TYPES},
                    {name: 'description', label: '简介', value: l.description, type: 'textarea'}
                ]
            })));
            actions.appendChild(wrapInDetails('改状态', buildForm({
                title: '改状态',
                endpoint: '/api/landmarks/' + l.uuid + '/status',
                method: 'PUT',
                submitLabel: '更新状态',
                requirePermission: 'Common',
                query: ['status'],
                fields: [{name: 'status', label: '状态', value: l.status, type: 'select', options: LANDMARK_STATUS}]
            })));
            actions.appendChild(buildDeleteButton('/api/landmarks/' + l.uuid, '删除', 'Common'));
            node.appendChild(actions);

            const coordFields = [
                {name: 'x', label: 'X', type: 'text', required: true},
                {name: 'y', label: 'Y', type: 'text', required: true},
                {name: 'z', label: 'Z', type: 'text', required: true},
                {
                    name: 'level',
                    label: '维度',
                    value: 0,
                    type: 'text',
                    required: true,
                    hint: '-1 下界 / 0 主世界 / 1 末地'
                }
            ];
            node.appendChild(buildPanel({
                title: '建造者', load: {endpoint: '/api/landmarks/' + l.uuid + '/builders', render: userChip},
                forms: [
                    {
                        title: '添加建造者',
                        endpoint: '/api/landmarks/' + l.uuid + '/builders/{userUuid}',
                        method: 'POST',
                        submitLabel: '添加',
                        requirePermission: 'Common',
                        fields: [{name: 'userUuid', label: '用户 UUID', type: 'text', required: true}]
                    },
                    {
                        title: '移除建造者',
                        endpoint: '/api/landmarks/' + l.uuid + '/builders/{userUuid}',
                        method: 'DELETE',
                        submitLabel: '移除',
                        requirePermission: 'Common',
                        fields: [{name: 'userUuid', label: '用户 UUID', type: 'text', required: true}]
                    }
                ]
            }));
            node.appendChild(buildPanel({
                title: '坐标', load: {endpoint: '/api/landmarks/' + l.uuid + '/coordinates', render: coordRow},
                forms: [
                    {
                        title: '添加坐标',
                        endpoint: '/api/landmarks/' + l.uuid + '/coordinates',
                        method: 'POST',
                        submitLabel: '添加',
                        requirePermission: 'Common',
                        numbers: ['x', 'y', 'z', 'level'],
                        fields: coordFields
                    },
                    {
                        title: '移除坐标',
                        endpoint: '/api/landmarks/' + l.uuid + '/coordinates',
                        method: 'DELETE',
                        submitLabel: '移除',
                        requirePermission: 'Common',
                        numbers: ['x', 'y', 'z', 'level'],
                        fields: coordFields
                    }
                ]
            }));
            node.appendChild(buildPanel({
                title: '图片',
                load: {
                    endpoint: '/api/landmarks/' + l.uuid + '/pictures',
                    render: function (url) {
                        return pictureRow(url, l.uuid);
                    }
                },
                uploads: [{
                    title: '上传图片',
                    endpoint: '/api/landmarks/' + l.uuid + '/pictures/upload',
                    label: '上传',
                    multiple: true,
                    requirePermission: 'Common'
                }]
            }));
            node.appendChild(buildPanel({
                title: '子地标',
                load: {endpoint: '/api/landmarks/' + l.uuid + '/children', render: landmarkChip}
            }));
            node.appendChild(buildPanel({
                title: '评论',
                load: {
                    endpoint: '/api/landmarks/' + l.uuid + '/comments',
                    render: function (c) {
                        return TILE_BUILDERS['/api/comments'](c);
                    }
                },
                forms: [{
                    title: '发表评论',
                    endpoint: '/api/landmarks/' + l.uuid + '/comments',
                    method: 'POST',
                    submitLabel: '发表',
                    requirePermission: 'Common',
                    fields: [
                        {name: 'content', label: '评论内容', type: 'textarea', required: true},
                        {
                            name: 'score',
                            label: '评分（可选）',
                            type: 'select',
                            options: [{value: '', label: '不评分'}].concat(SCORES)
                        }
                    ]
                }]
            }));
            return node;
        },

        '/api/notices': function (n) {
            const node = tile(n.uuid, [['标题', n.title], ['内容', n.content], ['发布', fmtTime(n.publishTime)]]);
            const actions = el('div', 'tile-actions');
            actions.appendChild(wrapInDetails('编辑', buildForm({
                title: '编辑',
                endpoint: '/api/notices/' + n.uuid,
                method: 'PUT',
                submitLabel: '保存',
                requirePermission: 'Admin',
                fields: [
                    {name: 'title', label: '标题', value: n.title, type: 'text', required: true},
                    {name: 'content', label: '内容', value: n.content, type: 'textarea', required: true}
                ]
            })));
            actions.appendChild(buildDeleteButton('/api/notices/' + n.uuid, '删除', 'Admin'));
            node.appendChild(actions);
            return node;
        },

        '/api/message-boards': function (m) {
            const node = tile(m.uuid, [
                ['内容', m.content], ['评分', scoreLabel(m.score)],
                ['留言者', m.publisherUuid], ['时间', fmtTime(m.createTime)]
            ]);
            const actions = el('div', 'tile-actions');
            actions.appendChild(wrapInDetails('编辑', buildForm({
                title: '编辑',
                endpoint: '/api/message-boards/' + m.uuid,
                method: 'PUT',
                submitLabel: '保存',
                requirePermission: 'Common',
                fields: [
                    {name: 'content', label: '内容', value: m.content, type: 'textarea', required: true},
                    {name: 'score', label: '评分', value: m.score, type: 'select', options: SCORES}
                ]
            })));
            actions.appendChild(buildDeleteButton('/api/message-boards/' + m.uuid, '删除', 'Common'));
            node.appendChild(actions);
            node.appendChild(buildPanel({
                title: '回复',
                load: {endpoint: '/api/message-boards/' + m.uuid + '/replies', render: textRow}
            }));
            return node;
        },

        '/api/comments': function (c) {
            const node = tile(c.uuid, [
                ['内容', c.content], ['评分', scoreLabel(c.score)], ['发布者', c.publisherUuid],
                ['父评论', c.parentUuid || '—'], ['时间', fmtTime(c.createTime)]
            ]);
            const actions = el('div', 'tile-actions');
            actions.appendChild(wrapInDetails('编辑', buildForm({
                title: '编辑',
                endpoint: '/api/comments/' + c.uuid,
                method: 'PUT',
                submitLabel: '保存',
                requirePermission: 'Common',
                fields: [{name: 'content', label: '内容', value: c.content, type: 'textarea', required: true}]
            })));
            actions.appendChild(buildDeleteButton('/api/comments/' + c.uuid, '删除', 'Common'));
            node.appendChild(actions);
            node.appendChild(buildPanel({
                title: '回复', load: {endpoint: '/api/comments/' + c.uuid + '/replies', render: commentChip},
                forms: [{
                    title: '发表回复',
                    endpoint: '/api/comments',
                    method: 'POST',
                    submitLabel: '回复',
                    requirePermission: 'Common',
                    fields: [
                        {name: 'content', label: '回复内容', type: 'textarea', required: true},
                        {name: 'parentUuid', value: c.uuid, type: 'hidden'}
                    ]
                }]
            }));
            return node;
        },

        '/api/users': function (u) {
            const node = tile(u.uuid, [
                ['简介', u.introduction], ['UUID', u.uuid]
            ]);
            // 权限着色 +「我」高亮：CSS 据磁贴根类名上色
            node.classList.add(permClass(u.permission));
            const me = getCurrentUser();
            if (me && me.uuid === u.uuid) node.classList.add('is-me');
            // 头部：头像 + 用户名 + 内联标签 + 权限徽章 +「我」标识（取代独立标签面板）
            node.insertBefore(userHead(u), node.firstChild);
            const actions = el('div', 'tile-actions');
            const detail = el('a', 'tile-action tile-detail-link', '详情页');
            detail.href = 'user.html?uuid=' + encodeURIComponent(u.uuid);
            actions.appendChild(detail);
            // 账户信息（用户名/简介/重置密码）：仅本人或开发者可操作，管理员角色不再自动放行
            if (canManageAccount(u.uuid)) {
                actions.appendChild(wrapInDetails('改资料', buildForm({
                    title: '改资料',
                    endpoint: '/api/users/' + u.uuid,
                    method: 'PUT',
                    submitLabel: '保存',
                    requirePermission: 'Common',
                    fields: [
                        {name: 'name', label: '用户名', value: u.name, type: 'text', minlength: 3, maxlength: 16},
                        {name: 'introduction', label: '简介', value: u.introduction, type: 'textarea'}
                    ]
                })));
                actions.appendChild(wrapInDetails('重置密码', buildForm({
                    title: '重置密码',
                    endpoint: '/api/users/' + u.uuid + '/password',
                    method: 'PUT',
                    submitLabel: '重置密码',
                    requirePermission: 'Common',
                    fields: [{
                        name: 'password',
                        label: '新密码',
                        type: 'password',
                        required: true,
                        minlength: 6,
                        maxlength: 18
                    }]
                })));
            }
            actions.appendChild(wrapInDetails('改权限', buildForm({
                title: '改权限',
                endpoint: '/api/users/' + u.uuid + '/permission',
                method: 'PUT',
                submitLabel: '更新权限',
                requirePermission: 'Admin',
                query: ['permission'],
                fields: [{name: 'permission', label: '权限', value: u.permission, type: 'select', options: PERMISSIONS}]
            })));
            actions.appendChild(buildDeleteButton('/api/users/' + u.uuid, '删除', 'Admin'));
            node.appendChild(actions);
            node.appendChild(buildPanel({
                title: '头像',
                uploads: [{
                    title: '上传头像',
                    endpoint: '/api/users/' + u.uuid + '/avatar/upload',
                    label: '上传',
                    accept: 'image/*',
                    requirePermission: 'Common',
                    onSuccess: function () {
                        return refreshSession();
                    }
                }]
            }));
            // 标签管理（仅管理员）：展开时加载当前标签，并提供添加/移除表单
            node.appendChild(buildPanel({
                title: '标签',
                load: {endpoint: '/api/users/' + u.uuid + '/tags', render: tagRow},
                forms: [
                    {
                        title: '添加标签',
                        endpoint: '/api/users/' + u.uuid + '/tags',
                        method: 'POST',
                        submitLabel: '添加',
                        requirePermission: 'Admin',
                        query: ['tag'],
                        fields: [{name: 'tag', label: '标签', type: 'text', required: true, maxlength: 16}]
                    },
                    {
                        title: '移除标签',
                        endpoint: '/api/users/' + u.uuid + '/tags',
                        method: 'DELETE',
                        submitLabel: '移除',
                        requirePermission: 'Admin',
                        query: ['tag'],
                        fields: [{name: 'tag', label: '标签', type: 'text', required: true}]
                    }
                ]
            }));
            return node;
        }
    };

    // ---------------- 列表加载 ----------------
    const PAGE_SIZE = 20;

    async function loadList(container, page) {
        const endpoint = container.dataset.endpoint;
        if (!endpoint) return;
        const builder = TILE_BUILDERS[endpoint];
        const targetPage = (page === undefined || page === null || page < 0) ? 0 : page;
        container.textContent = '';
        const listBox = el('div', 'tile-list-items');
        const pagerBox = el('div', 'tile-pager');
        container.appendChild(listBox);
        container.appendChild(pagerBox);
        try {
            const sep = endpoint.indexOf('?') === -1 ? '?' : '&';
            const url = endpoint + sep + 'page=' + targetPage + '&size=' + PAGE_SIZE;
            const data = await apiFetch(url);
            const items = (data && Array.isArray(data.content)) ? data.content : [];
            if (items.length === 0) {
                listBox.appendChild(el('p', 'empty-hint', '暂无数据。'));
            } else {
                items.forEach(function (item) {
                    const node = builder
                        ? builder(item)
                        : tile(item.uuid, [['JSON', JSON.stringify(item)]]);
                    listBox.appendChild(node);
                });
            }
            renderPager(pagerBox, container, data, targetPage);
        } catch (e) {
            listBox.appendChild(el('p', 'empty-hint error', '加载失败：' + e.message));
        }
    }

    // 分页控件：上一页 / 下一页 + 页码信息（数据来自后端 PageResponse）
    function renderPager(box, container, data, currentPage) {
        if (!data) return;
        const totalPages = data.totalPages || 0;
        const total = data.totalElements || 0;
        if (totalPages <= 1) {
            box.appendChild(el('span', 'pager-info', '共 ' + total + ' 条'));
            return;
        }
        const prev = el('button', 'tile-action pager-btn', '上一页');
        prev.type = 'button';
        prev.disabled = !!data.first;
        prev.addEventListener('click', function () {
            loadList(container, currentPage - 1);
        });
        const next = el('button', 'tile-action pager-btn', '下一页');
        next.type = 'button';
        next.disabled = !!data.last;
        next.addEventListener('click', function () {
            loadList(container, currentPage + 1);
        });
        const info = el('span', 'pager-info',
            '第 ' + (currentPage + 1) + ' / ' + totalPages + ' 页 · 共 ' + total + ' 条');
        box.appendChild(prev);
        box.appendChild(info);
        box.appendChild(next);
    }


    function loadAllLists() {
        document.querySelectorAll('.tile-grid[data-endpoint]').forEach(function (c) {
            loadList(c, 0);
        });
        // 详情页：任何改动后重新加载单个地标（评论/图片/编辑等提交都会走到这里）
        if (document.getElementById('landmark-detail')) {
            initLandmarkDetail();
        }
        // 玩家详情页：改动资料/权限/标签后重新加载单个用户
        if (document.getElementById('user-detail')) {
            initUserDetail();
        }
    }

    // 地标详情页：按 URL 的 ?uuid= 加载单个地标并复用磁贴构建器渲染
    async function initLandmarkDetail() {
        const box = document.getElementById('landmark-detail');
        if (!box) return;
        const uuid = new URLSearchParams(location.search).get('uuid');
        box.textContent = '';
        if (!uuid) {
            box.appendChild(el('p', 'empty-hint error', '缺少地标 UUID 参数（?uuid=…）。'));
            return;
        }
        box.appendChild(el('p', 'empty-hint', '加载中…'));
        try {
            const l = await apiFetch('/api/landmarks/' + encodeURIComponent(uuid));
            box.textContent = '';
            box.appendChild(TILE_BUILDERS['/api/landmarks'](l));
        } catch (e) {
            box.textContent = '';
            box.appendChild(el('p', 'empty-hint error', '加载失败：' + e.message));
        }
    }

    // 玩家详情页：按 URL 的 ?uuid= 加载单个用户并复用磁贴构建器渲染
    async function initUserDetail() {
        const box = document.getElementById('user-detail');
        if (!box) return;
        const uuid = new URLSearchParams(location.search).get('uuid');
        box.textContent = '';
        if (!uuid) {
            box.appendChild(el('p', 'empty-hint error', '缺少用户 UUID 参数（?uuid=…）。'));
            return;
        }
        box.appendChild(el('p', 'empty-hint', '加载中…'));
        try {
            const u = await apiFetch('/api/users/' + encodeURIComponent(uuid));
            box.textContent = '';
            box.appendChild(TILE_BUILDERS['/api/users'](u));
        } catch (e) {
            box.textContent = '';
            box.appendChild(el('p', 'empty-hint error', '加载失败：' + e.message));
        }
    }

    // ---------------- 顶层表单权限门控（静态创建表单） ----------------
    function applyPermissionGating() {
        const user = getCurrentUser();
        document.querySelectorAll('section.form-section form.tile-form').forEach(function (form) {
            const id = form.id || '';
            if (id === 'login-form' || id === 'register-form') return; // 认证表单不门控
            const required = form.dataset.requirePermission || 'Common';
            const ok = !!(user && hasPermission(user.permission, required));
            const submit = form.querySelector('[type="submit"]');
            if (submit) submit.disabled = !ok;
            form.classList.toggle('disabled', !ok);
            if (!ok)
                showMsg(form, can0Hint(required), !ok)
        });
    }

    // ---------------- 首页：主镇轮播图 + 简介 ----------------
    async function initMainTown() {
        const carousel = document.getElementById('main-town-carousel');
        const intro = document.getElementById('main-town-intro');
        if (!carousel || !intro) return; // 非首页没有这两个元素，直接跳过

        let town;
        try {
            town = await apiFetch('/api/towns/main');
        } catch (e) {
            return; // 无主镇（404）或加载失败：两栏保持隐藏，优雅降级
        }

        // 简介栏
        document.getElementById('main-town-description').textContent = town.description || '';
        intro.hidden = false;

        // 轮播栏：拿不到图片或图片为空就不显示
        let urls = [];
        try {
            urls = await apiFetch('/api/towns/' + town.uuid + '/pictures') || [];
        } catch (e) { /* 图片加载失败按无图处理 */
        }
        if (!urls.length) return;

        carousel.hidden = false;
        startCarousel(carousel, urls);
    }

    function startCarousel(root, urls) {
        const viewport = root.querySelector('#carousel-viewport');
        const dotsBox = root.querySelector('#carousel-dots');
        let index = 0, timer = null;

        urls.forEach(function (url, i) {
            const img = document.createElement('img');
            img.src = url;
            img.alt = '主镇图片 ' + (i + 1);
            img.className = 'carousel-slide';
            viewport.appendChild(img);

            const dot = document.createElement('button');
            dot.type = 'button';
            dot.className = 'carousel-dot';
            dot.setAttribute('aria-label', '第 ' + (i + 1) + ' 张');
            dot.addEventListener('click', function () {
                show(i);
                restart();
            });
            dotsBox.appendChild(dot);
        });

        function show(i) {
            index = (i + urls.length) % urls.length;
            viewport.style.transform = 'translateX(-' + index * 100 + '%)';
            dotsBox.querySelectorAll('.carousel-dot').forEach(function (d, j) {
                d.classList.toggle('active', j === index);
            });
        }

        function restart() {
            clearInterval(timer);
            if (urls.length > 1) timer = setInterval(function () {
                show(index + 1);
            }, 5000);
        }

        root.querySelector('#carousel-prev').addEventListener('click', function () {
            show(index - 1);
            restart();
        });
        root.querySelector('#carousel-next').addEventListener('click', function () {
            show(index + 1);
            restart();
        });

        if (urls.length <= 1) { // 只有一张图时隐藏翻页按钮和指示点
            root.querySelectorAll('.carousel-btn, .carousel-dots').forEach(function (n) {
                n.style.display = 'none';
            });
        }
        show(0);
        restart();
    }

    // ---------------- 初始化 ----------------
    async function init() {
        await refreshSession();
        await loadPrivilegedTags();
        document.querySelectorAll('form.tile-form').forEach(wireForm);
        document.querySelectorAll('form.tile-form').forEach(prefillSelfUuid);
        applyPermissionGating();
        await initMainTown();
        loadAllLists();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();