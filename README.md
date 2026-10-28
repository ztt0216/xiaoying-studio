# 小影工坊 · 素材制作网站

用户浏览模板 → 上传图片 → 提交制作任务 → 任务在 Java 后台分三步异步执行（素材检查 → 模拟生成 → 结果整理）→ 期间可继续使用网站 → 在历史记录中查看状态、失败原因或结果图。
不调用真实 AI：「模拟生成」把用户上传的图片按模板拼成一张图，耗时和失败都可控。

```bash
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
没有引入额外的状态管理库、UI 库、消息队列或 Redis。

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

数据卷：`mysql-data`（数据库）、`uploads`（上传素材与结果图）。
完全重置：`docker compose down -v`。

### 3.2 本地开发（不用 Docker 跑应用）

```bash
# 1) MySQL：任选其一，执行 db/01-schema.sql 与 db/02-data.sql
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

## 4. 配置项

所有值都是本地演示用的非敏感默认值，见 `.env.example`。

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地 studio/studio | 数据库连接 |
| `STORAGE_DIR` | `./data`（容器内 `/data`） | 上传文件与结果图根目录 |
| `WORKER_THREADS` | 2 | 后台线程数；设为 1 时任务排队串行执行 |
| `WORKER_QUEUE` | 200 | 等待队列长度，满了以后新任务直接标记失败 |
| `SIM_STEP_DELAY_MS` | 3000 | 每个阶段的模拟耗时 |
| `SIM_FAIL_STAGE` | NONE | 让指定阶段失败：`NONE` / `ASSET_CHECK` / `GENERATE` / `FINALIZE` |
| `SIM_DEV_API` | true | 是否开放 `/api/dev/simulation` 运行时调整接口 |
| `BACKEND_URL`（前端构建参数） | `http://localhost:8080` | Next.js 代理目标 |

## 5. 数据与素材准备

- **表结构** `db/01-schema.sql`：`app_user`、`user_token`、`template`、`asset`、`task`、`task_asset`、`task_step`。
- **初始化数据** `db/02-data.sql`：4 个分类共 8 个模板，所需图片数为 1、2 或 3 张。其中「萌宠肖像」只接受 PNG 且不超过 2MB，「圣诞贺卡」只接受 JPEG，用来演示不同的校验规则。另外预置了演示用户 `alice`、`bob`。
- **封面** `backend/src/main/resources/static/demo/*.png`：由 Java 以 `/demo/**` 静态资源提供。
- **演示上传文件** `samples/`：`sample-1..4.jpg/png` 是正常图片；`too-large-4mb.png` 用于在 2MB 限制的模板上演示超限；`not-an-image.png` 是扩展名为 png 的文本文件，用于演示魔数校验。
- 以上图片都是 `java tools/GenerateDemoImages.java` 生成的几何图形，没有版权问题，可以重新生成。

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
| `GET /api/tasks?templateId=&limit=` | 当前用户的历史，按创建时间倒序 | – | `Task[]` |
| `GET /api/tasks/{id}` | 单个任务及阶段记录；**只读**，不推进状态 | – | `Task`（带 `steps`）；他人任务返回 404 |
| `GET/PUT /api/dev/simulation` | 查看或修改模拟配置 | `{ stepDelayMs?, failStage? }` | `{ stepDelayMs, failStage, stages }` |

`Task` 字段：`id, templateId, templateTitle, templateCoverUrl, status, stage, failedStage, errorMessage, resultUrl, inputUrls[], steps[], createdAt, updatedAt, startedAt, finishedAt`。
`steps[]`：`{ stage, status: RUNNING|SUCCEEDED|FAILED, message, startedAt, finishedAt }`。

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
   - **结果整理**：把产物移到 `results/`，生成 `/files/results/task-{id}.png`。
5. 终态保护：转为成功的 SQL 带 `WHERE status='PROCESSING'`，转为失败的 SQL 带 `WHERE status IN ('PENDING','PROCESSING')`，所以已结束的任务不会被改回处理中。查询接口只读数据库，不会推动任务前进。
6. 关闭浏览器不影响任务，因为执行完全在 Java 线程池里进行。

## 8. 如何演示

先用 `alice` 登录（右上角“登录”→ 点 alice）。在模板详情页点击上传位，从 `samples/` 选图。

| 场景 | 操作 |
| --- | --- |
| **成功** | 打开「旅行明信片」，上传 2 张图，点“立即制作”。按钮会立刻恢复，右侧 History 出现任务卡片，三段进度依次点亮；完成后点击卡片可预览大图。 |
| **指定阶段失败** | 打开 http://localhost:3000/dev（页面不在导航中），把“失败阶段”设为 `模拟生成` 并保存，然后提交任务：任务在模拟生成阶段失败，卡片显示失败阶段和原因，结果整理阶段不会执行。设置会一直生效，可以重复触发，直到改回“不注入失败”。也可以用命令行：`curl -X PUT localhost:8080/api/dev/simulation -H 'Content-Type: application/json' -d '{"failStage":"ASSET_CHECK"}'`，或者用环境变量 `SIM_FAIL_STAGE=FINALIZE docker compose up`。 |
| **调整耗时** | 在 `/dev` 把每阶段耗时改成 10000ms，或设置 `SIM_STEP_DELAY_MS`，方便观察处理中状态。 |
| **多任务互不串扰** | 在 `/dev` 设置失败阶段为 `GENERATE`，提交任务 A；改回 `NONE`，再提交任务 B。模拟配置在投递时取快照，所以 A 失败、B 成功，两张卡片的阶段、错误和结果各自独立。顶部会显示“N 个任务处理中”。设 `WORKER_THREADS=1` 可以看到任务排队（B 先停在“等待处理”）。 |
| **刷新恢复** | 任务处理中直接刷新页面或切到“我的作品”：记录从 Java 重新拉取，未结束的任务会继续被跟踪直到结束。 |
| **查询异常 ≠ 任务失败** | 任务处理中执行 `docker compose stop backend`：卡片保持原来的“处理中”状态，并显示黄色提示“状态查询失败，已保留上次结果”和“重新查询”按钮。连续失败 5 次后暂停自动查询。`docker compose start backend` 后点“重新查询”即可恢复（重启会让这个任务按第 11 节的说明标记为失败）。 |
| **上传校验** | 在「萌宠肖像」上传 `too-large-4mb.png`（超过 2MB）或任意 JPG，前端会直接提示；绕过前端直接调接口时，后端同样返回 413 或 415。`not-an-image.png` 会被后端的魔数校验拒绝。 |
| **用户隔离** | 用 alice 提交任务后，在账户对话框里“切换”到 bob：历史为空，alice 的轮询被立即中止；切回 alice，记录又会出现。 |
| **命令行全流程** | `scripts/smoke-test.sh`（默认访问 http://localhost:3000）。 |

## 9. 前端异步状态管理

核心是 `web/lib/taskTracker.ts`（不依赖 React，有单元测试）。React 通过 `useSyncExternalStore` 订阅它。

- **区分不同状态**：上传状态由每个上传位自己维护（空 / 上传中及进度 / 已上传 / 失败可重试）；提交状态是 `submitting`，提交按钮同时用 ref 防止连点；任务状态来自后端的 `status/stage`；查询异常单独记在 `queryError` 和 `paused` 中。查询失败只显示提示，**不会**把任务改成 FAILED。
- **每个未结束任务有独立的轮询循环**：各自的定时器、`AbortController` 和错误计数，彼此不影响。任务结束后立即停止它的轮询；没有未结束任务时不发任何请求。失败时按指数退避重试，最长间隔 15 秒，连续失败 5 次后暂停，等用户手动“重新查询”或刷新列表。
- **防止旧数据覆盖**：终态不会被非终态覆盖；同为非终态时，以 `updatedAt` 较新的数据为准。
- **用户切换与清理**：跟踪器按用户 ID 分实例（`key` 变化时重建），旧实例 `stop()` 会中止所有在途请求，迟到的响应会被丢弃。页面级请求（模板、历史）在离开页面或参数变化时中止。跟踪器挂在全局 Provider 上，所以离开详情页后任务仍在后台被跟踪，顶部指示器可见。
- 页面状态覆盖了：模板不存在（404）、加载中（骨架屏或提示）、空数据、请求失败（带重试按钮）、未登录（引导登录）。

## 10. 用户身份与个人记录

- **方案**：昵称登录。输入昵称后，若不存在就创建（`app_user`），然后签发随机 UUID 令牌（`user_token`）。前端把令牌存在 localStorage，每个请求带 `Authorization: Bearer`。Java 拦截器解析出当前用户；素材、任务的读写都带 `user_id` 条件，访问他人的任务返回 404。
- **取舍**：题目不要求额外的身份服务，所以没有做密码。这个方案能明确区分“谁的任务”，也能演示切换用户。正式环境需要换成密码/OAuth + 过期令牌（HttpOnly Cookie）。
- **演示**：右上角按钮打开账户对话框，可以看到昵称、ID、创建时间，也可以切换用户或退出。预置了 `alice` 和 `bob`，也可以输入任意新昵称。

## 11. 完成情况、已知限制与取舍

**已完成**：R-01 ~ R-14 的主要内容，包括模板列表与分类筛选、详情页与上传（选择、预览、替换、移除、进度、失败重试）、双端校验、异步三阶段流水线、阶段级执行记录、失败注入与耗时调整、多任务、刷新恢复、查询异常处理、用户隔离、CI、Docker Compose。

**已知限制 / 未实现**：

- **重启不续跑**：线程池在内存中，进程重启后不会续跑。`StartupRecovery` 在启动时把遗留的 PENDING/PROCESSING 任务标记为 FAILED（原因：“服务重启，任务已中断，请重新提交”），这样前端不会一直显示处理中。这个做法假设只有一个后端实例；多实例部署需要租约或心跳字段，或者引入真正的队列。
- **模拟配置不持久**：运行时修改的模拟配置只保存在内存，重启后恢复为环境变量的值。
- **提交幂等**：前端已防止重复点击，但后端没有幂等键。要做的话，可以加 `client_request_id` 并在 `(user_id, client_request_id)` 上建唯一索引。
- **文件访问控制**：`/files/**`（素材与结果）不鉴权，依靠随机 UUID 文件名，因为 `<img>` 无法携带 Authorization 头。正式环境应改为签名 URL 或 Cookie 鉴权。
- **未做的内容**：未清理孤儿素材文件；未做任务删除、下载、重新制作（题目不要求）；没有端到端浏览器测试（题目不要求），前端测试只覆盖状态管理核心。
- **身份方案**：见第 10 节，令牌不会过期。

**实际投入**：

主体实现是在 AI 编程工具（Claude Code）的一次会话中完成的，从建立项目骨架到首次提交约 25 分钟墙钟时间（2026-10-28 21:36 → 22:00）。各部分大致占比：

- 后端与数据库：约 35%；
- 前端：约 35%；
- 本地 MySQL 联调、浏览器实测、Docker Compose 冷启动验证：约 20%；
- CI 与 README：约 10%。

阅读题目、审阅代码和补充说明的人工时间不包含在上述数字内：_（请补充）_。

**取舍**：时间优先投入题目的两个重点（后端多步骤异步、前端异步状态），并且每条路径都做了真实运行验证。幂等提交、断点续跑、文件鉴权、令牌过期这几项只做了上面列出的说明，没有实现。

## 12. 部署与回滚

- **部署**：`git checkout <验收 commit>`，然后 `docker compose up --build -d`。数据保存在命名卷中，重新构建镜像不会丢数据。
- **回滚**：`git checkout <上一版本>`，然后再执行 `docker compose up --build -d`。数据库结构只用了 `CREATE TABLE IF NOT EXISTS`；如果以后有结构变更，应引入 Flyway 等迁移工具，并在回滚前确认迁移可逆。
- **备份**：`docker compose exec mysql mysqldump -ustudio -pstudio studio > backup.sql`；上传文件位于 `uploads` 卷。

## 13. 测试

- **后端**（`mvn verify`）：
  - `TaskPipelineTest`：阶段严格按顺序执行；每个阶段注入失败后，后续阶段都不执行，并记录失败阶段和原因；未预期的异常会被记为失败且不泄露细节；已结束的任务不会被推回处理中；阶段配置不完整时启动失败。
  - `StepsIntegrationTest`：用真实的三个阶段在临时目录里跑完整流水线，结果文件可读；素材丢失时在素材检查阶段失败。
  - `ImageSnifferTest`：魔数识别。
- **前端**（`npm test`）：`taskTracker.test.ts` 覆盖以下情况：多个任务各自独立更新，结束一个只停一个；已结束的任务不轮询；查询异常保留任务状态，不会标记为失败，恢复后继续；连续失败后暂停，手动重试可恢复；404 停止跟踪；终态不会被回退；stop 后迟到的响应被丢弃；start 后恢复跟踪。
- **CI**：`.github/workflows/ci.yml` 在 push 或 PR 时执行前端 lint、类型检查、测试、构建，以及后端 `mvn verify`，最后执行 `docker compose build`。

## 14. AI 使用说明

本项目使用了 AI 编程工具 Claude Code（agent 模式，可以读写文件、执行命令、操作浏览器）。如实说明：**绝大部分代码、配置和本 README 都由 AI 生成**。

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

其他修正：

- `FileStorage` 有两个构造器，Spring 无法确定使用哪一个，导致启动失败；已补上 `@Autowired`。
- React 19 的 `react-hooks/set-state-in-effect` 规则不允许在 effect 中同步 setState。为此抽出了 `useResource`，让“加载中”由请求 key 推导；跟踪器则改为 `start/stop` 生命周期，以兼容 StrictMode 的卸载后重新挂载。

## 15. 复用与来源

- 没有复用现有的业务代码或脚手架模板，Next.js 和 Spring Boot 的工程结构是手工搭建的。
- 依赖的开源框架见第 1 节。
- 模板封面和演示图片由 `tools/GenerateDemoImages.java` 生成。UI 只参考了题目截图的布局，没有使用原品牌素材。
