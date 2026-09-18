# 曲奇小镇（Cookie Town）

一个 Minecraft 社区网站：小镇、地标、公告、留言板、评论、用户体系，外加主镇展示与轮播图。
后端 Spring Boot + MySQL + JWT，前端为无构建的原生 HTML/JS，由后端直接托管静态页面。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置文件 config.json](#配置文件-configjson)
- [权限体系](#权限体系)
- [API 一览](#api-一览)
- [前端页面](#前端页面)
- [设计要点](#设计要点)

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4（`EnvironmentPostProcessor` 新包名）、Spring Data JPA、Spring MVC |
| 数据库 | MySQL（`mysql-connector-j`，`ddl-auto` 由配置驱动） |
| 认证 | JWT（HS256，jjwt），密码 BCrypt 加密 |
| 序列化 | Jackson 3（`tools.jackson`） |
| 辅助 | Lombok、jspecify 空值注解 |
| 前端 | 原生 HTML/CSS/JS（无构建、无依赖），托管于 `wwwroot/` |
| JDK | 21+（用到 `Math.clamp` 等新 API） |

## 项目结构

```
├── src/main/java/com/sympsel/
│   ├── config/          # AppConfig、AppConfigLoader、JsonConfigBootstrap、
│   │                    # DataSourceEnvironmentPostProcessor、WebConfig、SecurityConfig
│   ├── controller/      # Auth / User / Town / Landmark / Comment / Notice / MessageBoard / Config
│   ├── dto/             # 请求/响应记录类（record）
│   ├── entitys/         # JPA 实体（含 enums 与 metadatas）
│   ├── repository/      # Spring Data JPA Repository
│   ├── security/        # JwtService、JwtAuthFilter、UserContext、PermissionGuard、
│   │                    # RequirePermission、PermissionInterceptor、异常体系
│   ├── service/         # 业务服务 + FileStorageService
│   └── utils/           # PageUtil、UuidUtil
├── src/main/resources/
│   ├── application.yml
│   └── META-INF/spring.factories   # 注册 EnvironmentPostProcessor
├── config/config.json   # 外部配置唯一真源（见下文）
├── wwwroot/             # 前端静态页面（index.html、towns.html、js/app.js、css/）
└── uploads/             # 上传的图片（头像、小镇/地标轮播图），映射为 /uploads/**
```

## 快速开始

### 1. 准备 MySQL

```sql
CREATE DATABASE cookie_town DEFAULT CHARSET utf8mb4;
```

表结构由 JPA 自动建/更新（`ddl-auto` 见 `clean-and-launch` 配置）。

### 2. 编写 `config/config.json`

启动**必须**存在该文件（缺失会直接启动失败），最小可用示例：

```json
{
  "mysql": {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "your-password",
    "database": "cookie_town"
  },
  "jwt-secret": "请替换为至少32字节的随机字符串!!!!!!!!",
  "first-admin": ["admin", "admin123456"],
  "developers": ["admin"],
  "main-town": {
    "name": "曲奇小镇",
    "description": "欢迎来到曲奇小镇！",
    "ownerUsername": "admin",
    "pictures": []
  },
  "page-size": 20
}
```

### 3. 启动

```bash
./mvnw spring-boot:run        # 或打包后 java -jar target/cookie-town.jar
```

访问 `http://localhost:8080/`（`/` 自动转发到 `index.html`）。

首次启动后：`first-admin` 账号已就绪，`main-town` 已同步建镇，用管理员账号登录即可管理全站。

## 打包与部署脚本（模板）

以下脚本为模板，按需调整顶部变量（jar 名、目录名）后放入项目根目录使用。

### 打包脚本 `package.sh`（Linux/macOS）

构建 jar 并组装可分发目录 `dist/cookie-town/`：后端 jar + 前端 `wwwroot/` + 配置模板 + 空 `uploads/`。

```bash
#!/usr/bin/env bash
set -euo pipefail

APP_NAME="cookie-town"                 # 产物目录名
JAR_FILE=$(ls target/*.jar | grep -v '\.original$' | head -n 1)   # 跳过 Spring Boot 的 .original
DIST_DIR="dist/${APP_NAME}"

echo "==> 1/3 构建 jar"
./mvnw clean package -DskipTests

echo "==> 2/3 组装 ${DIST_DIR}"
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"/{config,uploads}
cp "${JAR_FILE}"            "${DIST_DIR}/${APP_NAME}.jar"
cp -r wwwroot               "${DIST_DIR}/wwwroot"
cp scripts/start.sh         "${DIST_DIR}/start.sh"   2>/dev/null || true

# 配置模板：首次部署生成，绝不上传真实密钥到仓库
if [[ ! -f "${DIST_DIR}/config/config.json" ]]; then
  cat > "${DIST_DIR}/config/config.json" <<'JSON'
{
  "mysql":    { "host": "localhost", "port": 3306, "user": "root", "password": "CHANGE_ME", "database": "cookie_town" },
  "jwt-secret": "CHANGE_ME_AT_LEAST_32_BYTES!!!!!!!!!",
  "first-admin": ["admin", "CHANGE_ME"],
  "developers":  ["admin"],
  "main-town":  { "name": "曲奇小镇", "description": "", "ownerUsername": "admin", "pictures": [] },
  "page-size": 20
}
JSON
fi
chmod +x "${DIST_DIR}/start.sh" 2>/dev/null || true

echo "==> 3/3 打包 dist/${APP_NAME}.tar.gz"
tar -czf "dist/${APP_NAME}.tar.gz" -C dist "${APP_NAME}"
echo "完成：dist/${APP_NAME}.tar.gz"
```

### 运行脚本 `start.sh`（start / stop / restart / status）

与 jar 同目录使用；`config/`、`uploads/`、`wwwroot/` 均以它为工作目录解析（与 `app.config-dir` 默认 `./config` 对应）。

```bash
#!/usr/bin/env bash
set -euo pipefail

APP_NAME="cookie-town"
JAR_FILE="$(cd "$(dirname "$0")" && pwd)/${APP_NAME}.jar"
PID_FILE="$(dirname "${JAR_FILE}")/${APP_NAME}.pid"
LOG_FILE="$(dirname "${JAR_FILE}")/${APP_NAME}.log"
JVM_OPTS="-Xms256m -Xmx512m"
# export CONFIG_DIR=/etc/cookie-town   # 如需把 config.json 放到其他目录

cd "$(dirname "${JAR_FILE}")"          # 关键：工作目录决定 ./config、./uploads、./wwwroot 的解析位置

is_running() { [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; }

case "${1:-start}" in
  start)
    if is_running; then echo "已在运行（PID $(cat "${PID_FILE}")）"; exit 0; fi
    nohup java ${JVM_OPTS} -jar "${JAR_FILE}" >> "${LOG_FILE}" 2>&1 &
    echo $! > "${PID_FILE}"
    echo "已启动（PID $(cat "${PID_FILE}")），日志：${LOG_FILE}"
    ;;
  stop)
    if is_running; then kill "$(cat "${PID_FILE}")" && rm -f "${PID_FILE}"; echo "已停止";
    else echo "未在运行"; fi
    ;;
  restart) "$0" stop; sleep 2; "$0" start ;;
  status)  is_running && echo "运行中（PID $(cat "${PID_FILE}")）" || echo "未运行" ;;
  *) echo "用法：$0 {start|stop|restart|status}"; exit 1 ;;
esac
```

### 打包脚本 `package.bat`（Windows，可选）

```bat
@echo off
setlocal
set APP_NAME=cookie-town
set DIST_DIR=dist\%APP_NAME%

call mvnw.cmd clean package -DskipTests || exit /b 1

rmdir /s /q "%DIST_DIR%" 2>nul
mkdir "%DIST_DIR%\config" "%DIST_DIR%\uploads%"
for %%f in (target\*.jar) do (echo %%f | findstr /v ".original" >nul && copy /y "%%f" "%DIST_DIR%\%APP_NAME%.jar" >nul)
xcopy /e /i /y wwwroot "%DIST_DIR%\wwwroot" >nul
echo 完成：%DIST_DIR%（config\config.json 请参照模板手动放入）
endlocal
```

> 部署注意：`start.sh` 里 `cd` 到 jar 所在目录是必须的——`config/`、`uploads/`、`wwwroot/` 都按**工作目录**相对解析；把 jar 换个目录单独启动会找不到 `config.json` 而启动失败。

## 配置文件 config.json

外部配置是**唯一真源**，默认目录 `./config`（相对应用工作目录），可用环境变量 `CONFIG_DIR` 覆盖。
`DataSourceEnvironmentPostProcessor` 在容器刷新前读取它并注入数据源/JWT/上传限制等属性；`JsonConfigBootstrap` 在启动后把它落地到数据库。

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `mysql` | object | — | **必填**。数据源连接信息（host/port/user/password/database） |
| `jwt-secret` | string | 空 | JWT 签名密钥，≥32 字节；留空回退环境变量 `JWT_SECRET` |
| `first-admin` | `[用户名, 密码]` | 空 | 首个管理员：不存在则创建，已存在则确保为 Admin（解决冷启动无管理员死锁） |
| `developers` | string[] | 空 | 开发者名单（用户名）：启动时同步「开发者」标签，名单内打标、名单外撤标 |
| `main-town` | object | 空 | 主镇：name/description/ownerUsername/pictures，幂等同步；镇长未注册时建为无主镇，其注册后重启自动回填 |
| `page-size` | int | 20 | 列表默认每页条数（上限 100） |
| `max-image-size` | int (MB) | 10 | 单张图片上传上限 |
| `max-request-size` | int (MB) | 20 | 单次请求上传上限 |
| `develop-mode` | bool | false | 开发模式开关（经 `/api/config/develop-mode` 暴露给前端） |
| `clean-and-launch` | bool | false | true → `ddl-auto=create`（每次启动重建表，**会清空数据**）；false → `update` |

其他环境变量：`DEVELOPER_TAG`（开发者标签名，默认「开发者」）、`JWT_SECRET`（jwt-secret 的兜底）。

> JSON 中任何 `comment-*` 等未知键会被忽略，可当注释使用。

## 权限体系

### 角色（`Permission` 枚举）

| 角色 | 说明 |
|---|---|
| `Admin` | 管理员：全站管理（用户、公告、越权内容处理） |
| `Common` | 成员：全部读写能力（建镇、发评论、传图等）。用户被加入小镇时自动由访客提权为成员 |
| `Visitor` | 访客：注册默认角色，只读 |
| `Marked` | 受限/黑名单：只读 |

### 开发者标签

拥有「开发者」标签的用户（名单由 config.json 的 `developers` 同步）可修复任意玩家的账户信息（用户名/简介/重置密码）。**Admin 角色本身不再自动通过账户类操作**——降低管理员权力，账户修复统一归开发者。

### 用户列表排序

用户列表按身份排序：**主镇镇长 > 开发者 > 普通管理员 > 成员 > 访客 > 黑名单**，同档内按创建时间倒序。

### 鉴权链路

- `JwtAuthFilter`：解析 `Authorization: Bearer <token>` 写入 `UserContext`（ThreadLocal）；所有 GET 公开，写操作（除注册/登录）必须携带有效 token；
- `@RequirePermission` + `PermissionInterceptor`：控制器方法级角色门控；
- `PermissionGuard.requireOwnerOrAdmin`：属主或 Admin 才能改/删对应内容（Service 层）；
- `UserService.requireSelfOrDeveloper`：账户信息仅本人或开发者可改。

## API 一览

所有接口前缀 `/api`，分页参数 `page`（默认 0）与 `size`（默认取 config 的 `page-size`，上限 100），返回统一的 `PageResponse` 包装。

### 认证 `/api/auth`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/login` | 公开 | 用户名+密码登录，返回 JWT 与当前用户信息 |
| GET | `/me` | 登录 | 当前登录用户信息 |

### 用户 `/api/users`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/users` | 公开 | 注册（默认 Visitor） |
| GET | `/api/users` | 公开 | 用户列表（按身份排序，分页） |
| GET | `/api/users/{uuid}` | 公开 | 用户详情 |
| PUT | `/api/users/{uuid}` | 本人/开发者 | 改用户名/简介 |
| PUT | `/api/users/{uuid}/password` | 本人/开发者 | 重置密码 |
| PUT | `/api/users/{uuid}/permission` | Admin | 调整角色 |
| POST/DELETE | `/api/users/{uuid}/tags` | Admin | 添加/移除自定义标签（GET 公开查询） |
| POST | `/api/users/{uuid}/avatar/upload` | 本人/Admin | 上传头像（固定文件名覆盖，自动清理旧文件） |
| DELETE | `/api/users/{uuid}` | Admin | 注销用户 |

### 小镇 `/api/towns`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/towns` | 公开 | 小镇列表（分页），响应含镇长用户名+uuid |
| GET | `/api/towns/main` | 公开 | 主镇信息（供首页简介；无主镇返回 404） |
| POST | `/api/towns` | Common+ | 创建小镇（创建者即镇长） |
| GET | `/api/towns/{uuid}` | 公开 | 小镇详情 |
| PUT/DELETE | `/api/towns/{uuid}` | 镇长/Admin | 编辑/删除 |
| GET | `/api/towns/{uuid}/children` | 公开 | 子城镇列表 |
| POST/DELETE | `/api/towns/{uuid}/members/{userUuid}` | 镇长/Admin | 添加/移除成员（**添加时自动把访客提权为成员**） |
| GET | `/api/towns/{uuid}/members` | 公开 | 成员列表 |
| GET | `/api/towns/{uuid}/pictures` | 公开 | 轮播图 URL 列表（供首页轮播） |
| POST | `/api/towns/{uuid}/pictures/upload` | 镇长/Admin | 上传轮播图（multipart，可多文件） |

### 地标 `/api/landmarks`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/landmarks`、`/{uuid}`、`/{uuid}/children` | 公开 | 列表/详情/子地标 |
| POST | `/api/landmarks` | Common+ | 创建地标（提交者） |
| PUT/DELETE | `/api/landmarks/{uuid}` | 提交者/Admin | 编辑/删除 |
| PUT | `/api/landmarks/{uuid}/status` | 提交者/Admin | 变更状态（正常/维护中/废弃） |
| POST/DELETE/GET | `/{uuid}/builders[/{userUuid}]` | 提交者/Admin（GET 公开） | 共建者管理 |
| POST/DELETE/GET | `/{uuid}/coordinates` | 提交者/Admin（GET 公开） | 坐标管理（x/y/z + 维度） |
| POST/DELETE/GET | `/{uuid}/pictures[/upload]` | 提交者/Admin（GET 公开） | 图片管理 |
| GET/POST | `/{uuid}/comments` | GET 公开 / Common+ | 地标评论（可带评分，聚合成地标总评分） |

### 评论 `/api/comments`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/comments`、`/{uuid}`、`/{uuid}/replies` | 公开 | 列表/详情/回复 |
| POST | `/api/comments` | Common+ | 发布评论 |
| PUT/DELETE | `/api/comments/{uuid}` | 作者/Admin | 编辑/删除 |

### 公告 `/api/notices`

GET 公开（列表/详情）；POST/PUT/DELETE 仅 **Admin**。

### 留言板 `/api/message-boards`

GET 公开（列表/详情/回复）；POST 需 Common+（可带评分）；PUT/DELETE 限作者/Admin。

### 配置 `/api/config`

`GET /privileged-tags`、`GET /develop-mode`、`GET /page-size`：公开，供前端 UI 门控（后端鉴权始终是权威）。

## 前端页面

`wwwroot/` 下纯静态页面，共享 `js/app.js`（会话管理、表单自动接线、磁贴渲染、权限门控）：

| 页面 | 功能 |
|---|---|
| `index.html` | 首页：主镇轮播图（无图自动隐藏）+ 主镇简介 + 功能导航 |
| `towns.html` | 小镇列表（镇长显示"用户名（uuid）"）、创建/编辑、成员与轮播图管理 |
| `landmarks.html` / `landmark.html` | 地标列表与详情 |
| `notices.html` | 公告 |
| `messageboards.html` | 留言板 |
| `comments.html` | 评论 |
| `users.html` / `user.html` | 用户列表（身份排序）与资料管理 |
| `login.html` / `register.html` | 登录 / 注册 |

## 设计要点

- **OSIV 关闭**（`open-in-view: false`）：懒加载集合（tags/pictures/members）一律在 Service 事务内取出映射为 DTO，避免 `LazyInitializationException`。
- **config.json 唯一真源**：数据源、JWT 密钥、管理员、开发者、主镇、分页均由外部配置驱动，环境变量仅作兜底，密钥不入库不入镜像。
- **uuid 弱引用**：实体间一律用 uuid 字符串关联（无数据库外键），删除不产生外键级联，业务层自行维护引用完整性。
- **上传安全**：`FileStorageService` 校验 Content-Type、扩展名白名单（png/jpg/jpeg/gif/webp）、大小上限，并做路径穿越防护；存储层替换为对象存储时只需改写该类。
- **幂等启动引导**：`JsonConfigBootstrap` 每步独立 try-catch，失败只记日志不阻断启动；开发者名单变更、主镇信息变更均在下次启动自动收敛。
- **事务一致性**：所有写操作在 `@Transactional` 内完成，多步写入（如建镇同时回写父镇子列表）同生共死。
