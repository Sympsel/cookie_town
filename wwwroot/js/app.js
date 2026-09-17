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

    // ---------------- 小工具 ----------------
    function fmtTime(ms) {
        if (!ms) return '';
        return new Date(ms).toLocaleString();
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
        fd.forEach(function (v, k) {
            // 可选字段留空则不提交（如 parentTownUuid / parentUuid）
            if (typeof v === 'string' && v.trim() === '') return;
            body[k] = v;
        });
        const missing = [];
        let url = form.dataset.endpoint || '';

        // 1) 路径参数：{name} 用同名字段替换并移出 body
        url = url.replace(/\{(\w+)\}/g, function (_, name) {
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

        return {url: url, body: body, missing: missing};
    }

    function showMsg(form, text, isError) {
        const m = form.querySelector('.form-message');
        if (!m) return;
        m.textContent = text || '';
        m.classList.toggle('error', !!isError);
        m.classList.toggle('success', !isError && !!text);
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
            showMsg(form, '提交中…', false);
            try {
                const hasBody = Object.keys(resolved.body).length > 0;
                const data = await apiFetch(resolved.url, {method: method, body: hasBody ? resolved.body : undefined});
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
    function userChip(u) {
        return tile(u.uuid, [['用户', u.name], ['权限', u.permission], ['UUID', u.uuid]]);
    }

    function coordRow(c) {
        return tile(null, [['坐标', '(' + c.x + ', ' + c.y + ', ' + c.z + ')'], ['维度', c.level]]);
    }

    function textRow(s) {
        const d = el('div', 'tile tile-item');
        d.appendChild(el('span', 'tile-val', String(s)));
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

    // ---------------- 各模块交互磁贴构建器（覆盖全部端点） ----------------
    const TILE_BUILDERS = {
        '/api/towns': function (t) {
            const node = tile(t.uuid, [
                ['名称', t.name], ['简介', t.description], ['评分', t.score],
                ['镇长', t.ownerUuid], ['创建', fmtTime(t.createTime)]
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
            return node;
        },

        '/api/landmarks': function (l) {
            const node = tile(l.uuid, [
                ['地标', l.name], ['类型', l.type], ['状态', l.status],
                ['简介', l.description], ['评分', l.score], ['提交者', l.submitterUuid]
            ]);
            const actions = el('div', 'tile-actions');
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
                title: '图片', load: {endpoint: '/api/landmarks/' + l.uuid + '/pictures', render: textRow},
                forms: [
                    {
                        title: '添加图片',
                        endpoint: '/api/landmarks/' + l.uuid + '/pictures',
                        method: 'POST',
                        submitLabel: '添加',
                        requirePermission: 'Common',
                        query: ['url'],
                        fields: [{name: 'url', label: '图片 URL', type: 'text', required: true}]
                    },
                    {
                        title: '移除图片',
                        endpoint: '/api/landmarks/' + l.uuid + '/pictures',
                        method: 'DELETE',
                        submitLabel: '移除',
                        requirePermission: 'Common',
                        query: ['url'],
                        fields: [{name: 'url', label: '图片 URL', type: 'text', required: true}]
                    }
                ]
            }));
            node.appendChild(buildPanel({
                title: '子地标',
                load: {endpoint: '/api/landmarks/' + l.uuid + '/children', render: landmarkChip}
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
                ['内容', m.content], ['评分', m.score],
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
                ['内容', c.content], ['发布者', c.publisherUuid],
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
                ['用户名', u.name], ['权限', u.permission],
                ['简介', u.introduction], ['UUID', u.uuid]
            ]);
            const actions = el('div', 'tile-actions');
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
                title: '标签',
                load: {endpoint: '/api/users/' + u.uuid + '/tags', render: textRow}
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
        document.querySelectorAll('.tile-grid[data-endpoint]').forEach(loadList);
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

    // ---------------- 初始化 ----------------
    async function init() {
        await refreshSession();
        document.querySelectorAll('form.tile-form').forEach(wireForm);
        document.querySelectorAll('form.tile-form').forEach(prefillSelfUuid);
        applyPermissionGating();
        loadAllLists();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
