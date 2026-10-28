# 小影工坊 · 素材制作网站

用户浏览模板 → 上传图片 → 提交制作任务 → 任务在 Java 后台分三步异步执行（素材检查 → 模拟生成 → 结果整理）→ 期间可继续使用网站 → 在历史记录中查看状态、失败原因或结果图。
不调用真实 AI：「模拟生成」把用户上传的图片按模板拼成一张图，耗时和失败都可控。

> **交付版本**：提交时注明本 README 所在提交的完整 SHA（`git rev-parse HEAD`），评估者按该 SHA 检出，确保源码与说明一致。
>
> **已验证的功能基线**：tag `v1.2`，对应提交 `74e2cc3ee7a5af43c71462cecd89029d4e792bea`。2026-10-28 已完成独立 Compose 冷启动、关键接口与 Safari 交互检查，见第 13 节。`v1.2` 保留原有功能快照，不包含后续 README 更新；`v1.0`、`v1.1` 为较早版本。

```bash
git clone https://github.com/ztt0216/xiaoying-studio.git
cd xiaoying-studio
# 如已收到交付 SHA，先执行 git checkout <交付SHA>
cp .env.example .env        # 可选，不复制则使用相同的默认值
docker compose up --build   # 打开 http://localhost:3000
```

---

## 1. 技术版本

| 部分 | 选型 | 版本 |
| --- | --- | --- |
| 前端 | Next.js（App Router）+ React + TypeScript | Next 16.4.1 / React 19.2.9 / TS 5.9.3 |
| 前端测试 / 规范 | Vitest / ESLint（eslint-config-next） | 3.2 / 9.39 |
| 后端 | Java + Spring Boot + MyBatis | Java 21 / Spring Boot 3.5.0 / mybatis-spring-boot-starter 3.0.4 |
| 数据库 | MySQL | 8.4 |
| 运行时 | Node.js / Docker Compose | Node 22 / Compose v2 |

依赖锁定：`web/package-lock.json`；Maven 版本由 Spring Boot parent 统一管理。
没有引入额外的状态管理库、UI 库、消息队列或 Redis。唯一的前端附加依赖是本地打包的字体 `@fontsource/barlow-condensed`。

## 2. 项目结构

```
.
├── backend/                     Spring Boot 服务（业务、异步执行、持久化都在这里）
│   └── src/main/java/com/xiaoying/studio/
│       ├── auth/                昵称登录、令牌、拦截器
│       ├── template/            模板查询
│       ├── asset/               上传、魔数校验、本地文件存储
│       ├── task/                任务受理、查询、线程池投递、流水线
│       │   └── steps/           三个阶段：AssetCheckStep / GenerateStep / FinalizeStep
│       ├── simulation/          模拟耗时 / 失败注入及开发接口
│       └── common/ config/      统一错误响应、线程池、静态文件映射
├── web/                         Next.js 前端
│   ├── app/                     页面：/、/templates/[id]、/history、/dev
│   ├── components/              上传面板、任务卡片、账户对话框等
│   └── lib/                     API 客户端、taskTracker（异步状态核心）、会话
├── db/                          01-schema.sql 建表、02-data.sql 模板与演示用户
├── samples/                     演示用上传图片（含超大文件、非图片文件）
├── tools/GenerateDemoImages.java  生成模板封面与演示素材的脚本
├── scripts/smoke-test.sh        命令行端到端冒烟脚本
├── docker-compose.yml  .env.example
└── .github/workflows/ci.yml
```

## 3. 运行方式

### 3.1 Docker Compose（推荐）

```bash
docker compose up --build
```

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| web | http://localhost:3000 | Next.js；把 `/api/*`、`/files/*`、`/demo/*` 反向代理到 backend |
| backend | http://localhost:8080 | Spring Boot |
| mysql | localhost:3307 | 首次启动自动执行 `db/` 下的建表和初始化脚本 |

首次构建需要联网下载依赖和基础镜像；应用启动后，模板、素材和模拟生成不依赖外部 AI、付费资源或公司内部服务。等待 MySQL 初始化和后端启动完成，再访问前端。

数据卷：`mysql-data`（数据库）、`uploads`（上传素材与结果图）。

- 查看状态与日志：`docker compose ps`、`docker compose logs -f backend web`。
- 停止并保留数据：`docker compose down`。
- 完全重置：`docker compose down -v`，会删除当前项目全部数据库与文件数据，仅用于确实需要清空的演示环境。
- 默认端口被占用时，在 `.env` 修改 `WEB_PORT`、`BACKEND_PORT`、`MYSQL_PORT`。
- `db/` 初始化脚本仅在 MySQL 数据卷为空时执行；修改 SQL 后重启不会自动重建已有数据库。

### 3.2 本地开发（验收优先使用第 3.5 节的容器命令）

后端与前端命令在独立终端执行。下方连接配置对应默认 Compose 数据库；自定义 `.env` 后同步修改用户名、密码和端口。

```bash
# 1) 仓库根目录：启动 MySQL，空数据卷会自动执行 db/ 下的脚本
docker compose up -d mysql

# 2) 后端（JDK 21+，Maven 3.9+）
cd backend
DB_URL='jdbc:mysql://localhost:3307/studio?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
  mvn spring-boot:run

# 3) 前端（Node 22）
cd web && npm ci && npm run dev     # BACKEND_URL 默认 http://localhost:8080
```

### 3.3 构建与测试命令

| 命令 | 作用 |
| --- | --- |
| `cd web && npm run lint` | ESLint |
| `cd web && npm run typecheck` | `tsc --noEmit` |
| `cd web && npm test` | Vitest 单元测试 |
| `cd web && npm run build` | 生产构建（standalone） |
| `cd backend && mvn verify` | Java 测试 + 打包 |
| `scripts/smoke-test.sh` | 对运行中的服务做端到端检查（需 curl、python3） |

### 3.4 前后端与数据库如何连接

- 浏览器只访问 Next.js（同源）。`web/next.config.ts` 的 rewrites 把 `/api`、`/files`、`/demo` 转发到 `BACKEND_URL`。rewrites 在 `next build` 时确定，Docker 中通过构建参数 `BACKEND_URL=http://backend:8080` 传入。
- Next.js 只做代理，不做业务。模板、素材、任务、历史都由 Java 提供并持久化到 MySQL。
- Java 通过 `DB_URL / DB_USERNAME / DB_PASSWORD` 连接 MySQL。时间统一按 UTC 存储（`connectionTimeZone=UTC`），前端按浏览器时区显示。

### 3.5 容器内构建与测试（验收）

以下命令在仓库根目录执行。宿主机仅需 Docker 和 Compose v2；源码只读挂载到容器，复制到临时目录后安装依赖与构建，避免在宿主机执行项目脚本。

```bash
# 前端 lint、类型检查、测试和生产构建
docker run --rm -v "$PWD:/source:ro" -w /tmp node:22-bookworm-slim \
  sh -lc 'cp -R /source/web /tmp/web && cd /tmp/web && npm ci && npm run lint && npm run typecheck && npm test && npm run build'

# Java 测试与打包
docker run --rm -v "$PWD:/source:ro" -w /tmp maven:3.9-eclipse-temurin-21 \
  sh -lc 'cp -R /source/backend /tmp/backend && cd /tmp/backend && mvn -B verify'
```

独立运行验收采用单独的项目名、端口和数据卷：

```bash
WEB_PORT=3100 BACKEND_PORT=8180 MYSQL_PORT=3317 \
  docker compose -p studio-check up --build -d

# 前端 http://localhost:3100；等待后端启动完成后运行冒烟脚本
docker run --rm --network studio-check_default -v "$PWD:/source:ro" \
  python:3.12-slim sh -lc \
  'apt-get update >/dev/null && apt-get install -y --no-install-recommends curl >/dev/null && bash /source/scripts/smoke-test.sh http://web:3000'

# 仅清理这一套临时验收环境及其测试数据
docker compose -p studio-check down -v
```

冒烟脚本会创建 `smoke`、`smoke-other` 演示账号及任务，并修改全局模拟配置；正常结束时失败阶段为 `NONE`、阶段耗时为 1500ms。异常中断时应手动检查 `/dev` 并恢复配置，因此推荐在独立环境运行。脚本输出含临时签名图片链接，仅用于本地验证，不应作为公开交付材料。

## 4. 配置项

`.env.example` 提供本地演示用的非敏感默认值。下表列出后端支持的环境变量；只有 `docker-compose.yml` 中显式声明的变量会从 `.env` 传入后端容器。

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地 studio/studio | 数据库连接 |
| `STORAGE_DIR` | `./data`（容器内 `/data`） | 上传文件与结果图根目录 |
| `FILE_URL_SECRET` | 开发用默认值 | `/files/**` 签名链接的 HMAC 密钥；默认值公开，仅供本地演示，部署时替换为私有随机长字符串 |
| `FILE_URL_TTL_SECONDS` | 3600 | 签名链接有效期，实际有效时长在 1～2 倍之间 |
| `WORKER_THREADS` | 2 | 后台线程数；设为 1 时任务排队串行执行 |
| `WORKER_QUEUE` | 200 | 等待队列长度，满了以后新任务直接标记失败 |
| `SIM_STEP_DELAY_MS` | 3000 | 每个阶段的模拟耗时 |
| `SIM_FAIL_STAGE` | NONE | 让指定阶段失败：`NONE` / `ASSET_CHECK` / `GENERATE` / `FINALIZE` |
| `SIM_DEV_API` | true | 是否开放 `/api/dev/simulation` 运行时调整接口 |
| `BACKEND_URL`（前端构建参数） | `http://localhost:8080` | Next.js 代理目标 |

Compose 已透传 `FILE_URL_SECRET`、`SIM_STEP_DELAY_MS`、`SIM_FAIL_STAGE` 和 `WORKER_THREADS`。`FILE_URL_TTL_SECONDS`、`WORKER_QUEUE`、`SIM_DEV_API` 尚未在 `backend.environment` 中声明，仅写入 `.env` 不会生效；需要使用覆盖文件，例如：

```yaml
# compose.override.yml，按需创建
services:
  backend:
    environment:
      FILE_URL_TTL_SECONDS: "3600"
      WORKER_QUEUE: "200"
      SIM_DEV_API: "false"
```

执行 `docker compose -f docker-compose.yml -f compose.override.yml up --build -d` 应用覆盖。关闭开发接口后，`/dev` 页面无法读写模拟配置；仍可用环境变量设置耗时和失败阶段。

## 5. 数据与素材准备

- **表结构** `db/01-schema.sql`：`app_user`、`user_token`、`template`、`asset`、`task`、`task_asset`、`task_step`。
- **初始化数据** `db/02-data.sql`：4 个分类共 8 个模板，所需图片数为 1、2 或 3 张。其中「萌宠肖像」只接受 PNG 且不超过 2MB，「圣诞贺卡」只接受 JPEG，用来演示不同的校验规则。另外预置了演示用户 `alice`、`bob`。
- **封面** `backend/src/main/resources/static/demo/*.png`：由 Java 以 `/demo/**` 静态资源提供。
  - 每张封面画出该模板的成品版式，例如明信片、拍立得、三联画、画框、相册；
  - 照片位数量与模板的 `requiredImageCount` 一致，用户看封面就知道要传几张。
- **演示上传文件** `samples/`：`sample-1..4.jpg/png` 是正常图片；`too-large-4mb.png` 用于在 2MB 限制的模板上演示超限；`not-an-image.png` 是扩展名为 png 的文本文件，用于演示魔数校验。
- 以上图片都是 `java tools/GenerateDemoImages.java`（在仓库根目录执行）用纯几何图形绘制的，不含真实照片和文字，没有版权问题，可以重新生成。

## 6. 主要接口

除登录、模板和开发接口外，都需要请求头 `Authorization: Bearer <token>`。错误统一返回 `{ "code": "...", "message": "..." }`。

| 方法与路径 | 说明 | 请求 | 响应 |
| --- | --- | --- | --- |
| `POST /api/session` | 昵称登录，昵称不存在时自动创建 | `{ nickname }` | `{ token, user: { id, nickname, createdAt } }` |
| `GET /api/me` | 当前用户 | – | `user` |
| `DELETE /api/session` | 退出（作废令牌） | – | 204 |
| `GET /api/templates?category=` | 分类与模板列表 | – | `{ categories: string[], items: Template[] }` |
| `GET /api/templates/{id}` | 模板详情，不存在时 404 | – | `{ id, title, category, coverUrl, description, requiredImageCount, acceptedMimeTypes, maxFileSizeBytes }` |
| `POST /api/assets` | 上传单张素材（multipart） | `templateId`、`file` | 201 `{ id, url, mimeType, sizeBytes, originalName }`；超限返回 413，类型不符返回 415 |
| `POST /api/tasks` | 提交制作任务，**受理后立即返回** | `{ templateId, assetIds: number[] }` | 202 `Task`（`status=PENDING`） |
| `GET /api/tasks?templateId=&active=&limit=` | 当前用户的历史，按创建时间倒序；`active=true` 只返回未结束的任务 | – | `Task[]` |
| `GET /api/tasks/{id}` | 单个任务及阶段记录；**只读**，不推进状态 | – | `Task`（带 `steps`）；他人任务返回 404 |
| `GET/PUT /api/dev/simulation` | 查看或修改模拟配置 | `{ stepDelayMs?, failStage? }` | `{ stepDelayMs, failStage, stages }` |

`resultUrl`、`inputUrls`，以及上传接口返回的 `url`，都是带 `exp` 和 `sig` 参数的**签名链接**。只有通过已鉴权的接口才能拿到，详见第 10 节。

`Task` 字段：`id, templateId, templateTitle, templateCoverUrl, status, stage, failedStage, errorMessage, resultUrl, inputUrls[], steps[], createdAt, updatedAt, startedAt, finishedAt`。
`steps[]`：`{ stage, status: RUNNING|SUCCEEDED|FAILED, message, startedAt, finishedAt }`。列表接口不附带完整步骤明细，单任务查询返回阶段记录；为 null 的字段可能省略。

列表默认返回最近最多 50 条，可设置 `limit=1..100`；前端全局恢复使用 `active=true&limit=100`。目前未实现分页。

## 7. 任务状态、阶段与后台执行

**整体状态** `status`：`PENDING`（已受理，等待线程）→ `PROCESSING` → `SUCCEEDED` / `FAILED`。
**当前阶段** `stage`：`ASSET_CHECK`（素材检查）→ `GENERATE`（模拟生成）→ `FINALIZE`（结果整理）。失败时另外记录 `failedStage` 和 `errorMessage`。

执行流程：

1. `POST /api/tasks` 在一个事务里完成三件事：校验模板存在、素材数量与模板一致、素材属于当前用户、类型和大小合规；然后写入 `task`（PENDING）和 `task_asset`；最后注册 `afterCommit` 回调。事务**提交后**才把任务 ID 投递到独立线程池 `task-worker-*`，避免工作线程读到还没提交的数据。接口随即返回 202，不等制作完成。
2. 工作线程从数据库重新加载任务和素材，交给 `TaskPipeline` 按枚举顺序执行三个 `PipelineStep`。每一步都会：
   - 用条件更新进入该阶段：`UPDATE task … WHERE status IN ('PENDING','PROCESSING')`；
   - 写一条 `task_step(RUNNING)`；
   - 执行模拟耗时和失败注入，再执行该阶段真正的逻辑；
   - 把 `task_step` 更新为 SUCCEEDED 或 FAILED。
3. 某一步抛出 `StepFailedException`（预期内的失败）或其他异常（只记录“内部错误”，不暴露堆栈），流水线就停止：写入 `failedStage` 和原因，后续阶段不再执行。三步都成功后写入 `resultUrl`，状态改为 SUCCEEDED。
4. 三个阶段各自做的事：
   - **素材检查**：重新确认文件存在，用魔数识别真实类型，检查大小和数量；
   - **模拟生成**：用 `ImageIO`/`Graphics2D` 把素材拼成一张 PNG，写到 `work/`；
   - **结果整理**：把产物移到 `results/`，文件名是随机 UUID，不能由任务 ID 推出。数据库只保存未签名的路径，接口返回时再签名。
5. 终态保护：转为成功的 SQL 带 `WHERE status='PROCESSING'`，转为失败的 SQL 带 `WHERE status IN ('PENDING','PROCESSING')`，所以已结束的任务不会被改回处理中。查询接口只读数据库，不会推动任务前进。
6. 关闭浏览器不影响任务，因为执行完全在 Java 线程池里进行。

## 8. 如何演示

先用 `alice` 登录（右上角“登录”→ 点 alice）。在模板详情页点击上传位，从 `samples/` 选图。

| 场景 | 操作 |
| --- | --- |
| **成功** | 打开「旅行明信片」，上传 2 张图，点“开始制作”。受理响应返回后按钮恢复，右侧“制作记录”出现任务卡片，三段进度依次点亮；完成后点击卡片可预览大图。 |
| **指定阶段失败** | 打开 http://localhost:3000/dev（页面不在导航中），把“失败阶段”设为 `模拟生成` 并保存，然后提交任务：任务在模拟生成阶段失败，卡片显示失败阶段和原因，结果整理阶段不会执行。设置会一直生效，可以重复触发，直到改回“不注入失败”。也可以用命令行：`curl -X PUT localhost:8080/api/dev/simulation -H 'Content-Type: application/json' -d '{"failStage":"ASSET_CHECK"}'`，或者用环境变量 `SIM_FAIL_STAGE=FINALIZE docker compose up`。 |
| **调整耗时** | 在 `/dev` 把每阶段耗时改成 10000ms，或设置 `SIM_STEP_DELAY_MS`，方便观察处理中状态。 |
| **多任务互不串扰** | 在 `/dev` 设置失败阶段为 `GENERATE`，提交任务 A；改回 `NONE`，再提交任务 B。模拟配置在投递时取快照，所以 A 失败、B 成功，两张卡片的阶段、错误和结果各自独立。顶部会显示“N 个任务处理中”。设 `WORKER_THREADS=1` 可以看到任务排队（B 先停在“排队中”）。 |
| **刷新恢复** | 任务处理中，在任意页面（包括首页）刷新：登录状态一确认，前端就调用 `GET /api/tasks?active=true` 拉回该用户最近最多 100 个未结束的任务。顶部“N 个任务处理中”马上恢复，这些任务会继续被跟踪直到结束。详情页和“我的作品”页还会各自加载历史记录（有条数限制，见第 6 节）。 |
| **查询异常 ≠ 任务失败** | 任务处理中执行 `docker compose stop backend`：卡片保持原来的“处理中”状态，并显示黄色提示“状态查询失败，已保留上次结果”和“重新查询”按钮。连续失败 5 次后暂停自动查询。`docker compose start backend` 后点“重新查询”即可恢复（重启会让这个任务按第 11 节的说明标记为失败）。 |
| **上传校验** | 在「萌宠肖像」上传 `too-large-4mb.png`（超过 2MB）或任意 JPG，前端会直接提示；绕过前端直接调接口时，后端同样返回 413 或 415。`not-an-image.png` 会被后端的魔数校验拒绝。 |
| **用户隔离** | 用 alice 提交任务后，在账户对话框里“切换”到 bob：历史为空，alice 的轮询被立即中止；切回 alice，记录又会出现。 |
| **签名文件链接** | 完成后点击成品大图确认显示；复制图片地址后去掉查询参数或修改 `sig`，应返回 403。链接含访问权限，不要公开分享。过期校验另由 `FileUrlSignerTest` 用固定时钟验证。 |
| **命令行全流程** | 按第 3.5 节在独立容器中运行 `scripts/smoke-test.sh`；脚本也接受服务地址参数。 |

## 9. 前端异步状态管理

核心是 `web/lib/taskTracker.ts`（不依赖 React，有单元测试）。React 通过 `useSyncExternalStore` 订阅它。

- **区分不同状态**：上传状态由每个上传位自己维护（空 / 上传中及进度 / 已上传 / 失败可重试）；提交状态是 `submitting`，提交按钮同时用 ref 防止连点；任务状态来自后端的 `status/stage`；查询异常单独记在 `queryError` 和 `paused` 中。查询失败只显示提示，**不会**把任务改成 FAILED。
- **每个未结束任务有独立的轮询循环**：各自的定时器、`AbortController` 和错误计数，彼此不影响。任务结束后立即停止它的轮询；没有未结束任务时不发任何请求。失败时按指数退避重试，最长间隔 15 秒，连续失败 5 次后暂停，等用户手动“重新查询”或刷新列表。
- **防止旧数据覆盖**：终态不会被非终态覆盖；同为非终态时，以 `updatedAt` 较新的数据为准。
- **用户切换与清理**：跟踪器按用户 ID 分实例（`key` 变化时重建），旧实例 `stop()` 会中止所有在途请求，迟到的响应会被丢弃。页面级请求（模板、历史）在离开页面或参数变化时中止。跟踪器挂在全局 Provider 上，所以离开详情页后任务仍在后台被跟踪，顶部指示器可见。
- **刷新后统一恢复**：跟踪器 Provider 在拿到当前用户后（包括刷新页面后重新确认登录），会拉取该用户最近最多 100 个未结束的任务交给跟踪器。恢复不依赖用户停在哪个页面。拉取失败会按退避重试，切换用户时中止。
- 页面状态覆盖了：模板不存在（404）、加载中（骨架屏或提示）、空数据、请求失败（带重试按钮）、未登录（引导登录）。

## 10. 用户身份与个人记录

- **方案**：昵称登录（2～20 位字母、数字、中文、下划线或连字符）。输入昵称后，若不存在就创建（`app_user`），然后签发随机 UUID 令牌（`user_token`）。前端把令牌存在 localStorage，每个请求带 `Authorization: Bearer`。Java 拦截器解析出当前用户；素材、任务的读写都带 `user_id` 条件，访问他人的任务返回 404。
- **文件隔离**：素材和结果图通过 `/files/**` 访问，`<img>` 无法携带 Authorization 头，所以采用签名链接：
  - 已鉴权的接口返回带 `exp`（过期时间）和 `sig`（`HMAC-SHA256(路径|exp)`）的地址；
  - `FileAccessFilter` 在请求到达静态资源处理器前校验签名，缺少签名、签名被篡改或已过期都返回 403；
  - 过期时间按 TTL 对齐到时间桶，同一桶内同一文件的 URL 不变，轮询时图片不会重复加载；
  - 文件名本身也是随机 UUID。
- **取舍**：题目不要求额外的身份服务，所以没有做密码。该方案用于演示记录归属和切换用户，不验证昵称持有者的真实身份；知道昵称的人也能以该昵称登录。正式环境需要密码或 OAuth 等身份验证，并采用过期令牌及合适的会话存储策略（例如 HttpOnly Cookie）。
- **演示**：右上角按钮打开账户对话框，可以看到昵称、ID、创建时间，也可以切换用户或退出。预置了 `alice` 和 `bob`，也可以输入任意新昵称。

## 11. 完成情况、已知限制与取舍

**已完成**：R-01 ~ R-14 的主要内容，包括模板列表与分类筛选、详情页与上传（选择、预览、替换、移除、进度、失败重试）、双端校验、异步三阶段流水线、阶段级执行记录、失败注入与耗时调整、多任务、刷新恢复、查询异常处理、用户隔离、CI、Docker Compose。

**已知限制 / 未实现**：

- **重启不续跑**：线程池在内存中，进程重启后不会续跑。`StartupRecovery` 在启动时把遗留的 PENDING/PROCESSING 任务标记为 FAILED（原因：“服务重启，任务已中断，请重新提交”），这样前端不会一直显示处理中。这个做法假设只有一个后端实例；多实例部署需要租约或心跳字段，或者引入真正的队列。
- **模拟配置不持久**：运行时修改的模拟配置只保存在内存，重启后恢复为环境变量的值。
- **提交幂等**：前端已防止重复点击，但后端没有幂等键。要做的话，可以加 `client_request_id` 并在 `(user_id, client_request_id)` 上建唯一索引。
- **文件访问控制**：`/files/**` 已改为签名链接加随机文件名（见第 10 节），但仍有以下边界：
  - 签名链接在有效期内（1～2 小时）可以被转发，拿到链接的人在过期前都能查看；
  - 退出登录不会让已经签出的链接立即失效；
  - 若需要即时撤销，应改为 Cookie 会话鉴权，或由后端按用户校验后再输出文件。
- **未做的内容**：未清理孤儿素材文件；未做任务删除、下载、重新制作（题目不要求）；没有端到端浏览器测试（题目不要求），前端测试只覆盖状态管理核心。
- **身份方案**：见第 10 节，昵称可直接登录，令牌不会自动过期。
- **记录条数与链接续期**：历史最多查询 100 条，全局恢复最多 100 个未完成任务；尚无分页。已结束任务停止轮询，长时间停留后如需重新加载已过期的图片链接，可刷新历史列表或页面。
- **模拟结果范围**：不同模板采用相同的横向拼贴生成逻辑，主题颜色和输入数量有所区别；封面为版式示意，模拟结果不保证复现封面效果。题目不要求真实 AI 或图片处理效果。

**实际投入**：

主体实现是在 AI 编程工具（Claude Code）的一次会话中完成的，从建立项目骨架到首次提交约 25 分钟墙钟时间（2026-10-28 21:36 → 22:00）。各部分大致占比：

- 后端与数据库：约 35%；
- 前端：约 35%；
- 本地 MySQL 联调、浏览器实测、Docker Compose 冷启动验证：约 20%；
- CI 与 README：约 10%。

另外，人工投入约 1 小时，用于阅读题目、审阅代码、实际运行，以及对照评审意见提出修改。

**取舍**：优先完成后端多步骤异步处理、前端异步状态管理和可运行的业务闭环。文件访问已采用随机文件名与限时 HMAC 签名链接；后端提交幂等、断点续跑、文件链接即时撤销和令牌自动过期尚未实现。具体运行验证范围见第 13 节。

## 12. 部署与回滚

- **部署**：`git checkout <交付SHA>`，检查 `.env` 及所需覆盖配置，再执行 `docker compose up --build -d`。通过 `docker compose ps`、后端日志及 `/api/health` 检查启动状态，再验证模板列表和一次完整制作流程。部署到演示机以外的环境时替换签名密钥、数据库默认密码，并按需关闭开发接口。
- **记录版本**：交付回复附仓库链接和 `git rev-parse HEAD` 的完整输出。`v1.2` 是已验证的功能基线；后续 README 更新以实际交付 SHA 为准，不移动旧 tag。数据保存在命名卷中，重新构建镜像不会主动清空数据。
- **回滚**：`git checkout <上一版本>`，然后再执行 `docker compose up --build -d`。数据库结构只用了 `CREATE TABLE IF NOT EXISTS`；如果以后有结构变更，应引入 Flyway 等迁移工具，并在回滚前确认迁移可逆。
- **备份**：默认演示配置可用 `docker compose exec -T mysql mysqldump -ustudio -pstudio --no-tablespaces studio > backup.sql`；自定义凭据后同步修改命令。另行备份 `uploads` 卷，恢复时保证数据库与文件一致。

## 13. 测试

- **后端**（`mvn verify`）：
  - `TaskPipelineTest`：阶段严格按顺序执行；每个阶段注入失败后，后续阶段都不执行，并记录失败阶段和原因；未预期的异常会被记为失败且不泄露细节；已结束的任务不会被推回处理中；阶段配置不完整时启动失败。
  - `StepsIntegrationTest`：用真实的三个阶段在临时目录里跑完整流水线，结果文件可读；素材丢失时在素材检查阶段失败。
  - `ImageSnifferTest`：魔数识别。
  - `FileUrlSignerTest`：有效签名、同一时间桶 URL 稳定；路径或过期时间被篡改、密钥不符、链接过期及缺少签名时拒绝。
- **前端**（`npm test`）：`taskTracker.test.ts` 覆盖以下情况：多个任务各自独立更新，结束一个只停一个；已结束的任务不轮询；查询异常保留任务状态，不会标记为失败，恢复后继续；连续失败后暂停，手动重试可恢复；404 停止跟踪；终态不会被回退；stop 后迟到的响应被丢弃；start 后恢复跟踪。
- **CI**：`.github/workflows/ci.yml` 在 push 或 PR 时执行前端 lint、类型检查、测试、构建，以及后端 `mvn verify`，最后执行 `docker compose build`。

### 13.1 已验证结果与范围

以下为 2026-10-28 对功能基线 `74e2cc3ee7a5af43c71462cecd89029d4e792bea` 的检查记录；后续文档提交的 CI 与本次功能实测分别记录。

| 检查方式 | 已验证内容 |
| --- | --- |
| [GitHub Actions](https://github.com/ztt0216/xiaoying-studio/actions/runs/40681453745) | 前端 lint、类型检查、测试、构建；Java 测试与打包；Compose 镜像构建，三个 job 均成功 |
| 独立 Compose 环境 | 新数据库与文件卷冷启动，自动初始化 8 个模板和 4 个分类；三张素材任务完成三阶段处理，结果 PNG 和历史可读取 |
| 容器内接口检查 | 分类筛选、模板 404、上传与数量校验、伪图片和超大文件拒绝、三个阶段分别失败并中断、多任务分别成功/失败、终态稳定、跨用户查询 404 |
| 签名文件访问 | 有效图片链接可用；无签名、篡改签名、过期参数返回 403；同一时间桶重复查询结果 URL 不变 |
| Safari 手动检查 | 素材选择、预览、替换、移除及缺素材时禁止提交；首页刷新恢复任务，结束后顶部提示消失；历史和结果预览 |

查询异常与任务失败的区分、多任务隔离、取消后的迟到响应由前端核心单元测试覆盖。小屏仅核对响应式样式，未做移动端真机兼容性测试；手动检查不等同于自动化端到端测试。临时验收环境已清理。

## 14. AI 使用说明

本项目使用 Claude Code 完成主体实现，使用 Codex 对照题目复核源码、检查 GitHub CI、在容器中验证业务流程并完善交付文档。**绝大部分代码、配置和 README 由 AI 生成或修改**；人工负责阅读要求、审阅、运行检查及提出修改意见。未提交私人聊天记录、账号或凭据。

**工作流程**

1. **提供上下文**：把题目截图（项目目标、R-01 ~ R-14、验收方式、范围说明）原样交给 AI，没有另外补充设计。
2. **拆解任务**：AI 先确认本机工具链（JDK、Maven、Node、Docker），然后按以下顺序推进，每一步都要求可以运行或测试：
   1. 数据库结构；
   2. 后端分层（身份、模板、素材、任务流水线、模拟控制）；
   3. 流水线单测；
   4. 用真实 MySQL 联调；
   5. 前端状态核心 `taskTracker` 及其单测；
   6. 页面；
   7. Compose 与 CI；
   8. README。
3. **关键设计取舍**：
   - 流水线只依赖 `TaskProgressRecorder` 接口，所以能用内存实现来测试阶段顺序和失败中断。
   - 前端异步状态放在不依赖 React 的 `TaskTracker` 里，用假定时器测试多任务隔离和查询异常。
4. **验证方式**：不只看测试结果，而是每一层都实际运行过：
   - 后端单测；
   - 用 curl 走通真实的接口流程；
   - 在浏览器中实测上传、多任务、刷新恢复，以及停掉后端后查询异常的表现；
   - 用 `docker compose down -v && up --build` 冷启动后跑 `scripts/smoke-test.sh`；
   - 在容器中手动重启后端，验证遗留任务会被标记为失败。

**例子：运行验证发现、而单测没有发现的问题**

后端单测全部通过，但第一次接上真实 MySQL 就暴露出两个问题：

- 模板标题返回乱码。查十六进制后确认是初始化 SQL 按 latin1 执行，中文被双重编码。修复方法是在脚本开头加 `SET NAMES utf8mb4`。
- 按分类筛选返回 500。日志显示 MyBatis 注解中用文本块拼接 SQL 时少了一个空格，变成 `SELECTid`。修正后重建数据库并重新验证。

**例子：根据评审意见修改**

首次提交后，按 R-13 对照检查，发现三处不足，于是交给 AI 修改：

- **刷新只在部分页面恢复**：只有详情页和历史页会拉取任务，在首页刷新后要进入这两个页面才能恢复跟踪。改为登录确认后全局拉取未结束的任务，后端新增 `active=true` 参数。
- **结果图可被枚举访问**：结果图命名为 `task-{id}.png` 且不需要鉴权，README 里“依靠随机文件名”的说法对它不成立。改为随机文件名加 HMAC 签名链接，并新增 `FileUrlSignerTest`，同时修正 README 的描述。
- **交付信息不全**：补齐人工投入、功能基线与交付 SHA 的使用方式；依据实际检查结果修正文件访问控制、配置透传和验证范围，避免说明与实现不一致。

其他修正：

- `FileStorage` 有两个构造器，Spring 无法确定使用哪一个，导致启动失败；已补上 `@Autowired`。
- React 19 的 `react-hooks/set-state-in-effect` 规则不允许在 effect 中同步 setState。为此抽出了 `useResource`，让“加载中”由请求 key 推导；跟踪器则改为 `start/stop` 生命周期，以兼容 StrictMode 的卸载后重新挂载。

## 15. 复用与来源

- 没有复用现有的业务代码或脚手架模板，Next.js 和 Spring Boot 的工程结构是手工搭建的。
- 依赖的开源框架见第 1 节。
- 模板封面和演示图片由 `tools/GenerateDemoImages.java` 生成。UI 只参考了题目截图的布局，没有使用原品牌素材。
- **视觉设计**：参考了 Anthropic 公开的 `frontend-design` skill（[anthropics/skills](https://github.com/anthropics/skills)）的设计方法，没有复用其中的代码。
  - 方向是从“素材冲印”这个主题出发确定的，并刻意避开常见的模板化默认风格。
  - 页面像看片灯箱，模板做成带白边的相纸，制作记录是一条胶片。
  - 任务状态由画面本身表达：排队是暗帧，处理中是安全灯下的负片，成功显影为正片，失败是起雾的废片。
- **字体**：
  - 帧号和序号使用 Barlow Condensed（SIL Open Font License 1.1），通过 npm 包 `@fontsource/barlow-condensed` 打包在本地，构建时不依赖 Google Fonts；
  - 中文使用系统自带的宋体和黑体。
