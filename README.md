# RuoYi Robot

RuoYi Robot 是社区独立开源项目，并非 RuoYi 官方项目。

RuoYi Robot is a Java 17 modular-monolith foundation with a MySQL-oriented backend and Vue 3 administration shell. Its Maven modules use `robot-platform-*` and its Java packages use `com.robot.platform`. Existing HTTP API paths and physical database table/sequence names are preserved for compatibility.

## Platform

- Backend reactor module: `robot-platform-server`
- Application entry point: `com.robot.platform.server.RobotPlatformApplication`
- Admin frontend: `robot-platform-ui-admin`
- Runtime baseline: Java 17, Spring Boot, MySQL, Redis, and Vue 3

MQTT configuration and callback protection are documented in [MQTT boundary](docs/mqtt-boundary.md).

## 本地运行机器人平台

### 前置条件

- JDK 17 与 Maven 3.9+；后端以 Java 17 编译和运行。
- Docker Compose v2（MySQL 8.4、Redis 7、EMQX 5.8）。
- Node.js 20.19+（或 22.12+）与 pnpm 9+，用于管理后台。当前 Node 20.12 不能运行 Vite 8。
- 不要把 MQTT 密钥、设备 secret、回调 token 或登录 token 提交进 `.env`、脚本输出或前端代码。

### 启动依赖与后端

在仓库根目录创建一个未提交的 `.env`，为 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、
`EMQX_NODE_COOKIE`、`EMQX_DASHBOARD_PASSWORD`、`MQTT_CALLBACK_TOKEN`、`MQTT_CLOUD_USERNAME` 和
`MQTT_CLOUD_PASSWORD` 填入本机强随机值。模拟器/闭环验证还需要与已激活设备相匹配的
`MQTT_USERNAME` 和 `MQTT_SECRET`。不要使用示例值部署到共享环境：

```bash
docker compose up -d --wait mysql redis emqx
mvn -pl robot-platform-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

Compose 首次启动会按顺序导入 `sql/mysql/ruoyi-vue-pro.sql`、`sql/mysql/quartz.sql` 和
`sql/mysql/robot-platform.sql` 到 `robot_platform`。若需要重新初始化本地数据，先停止 Compose，
再明确删除这一个项目的 Docker volumes 后重新启动；不要对共享数据库执行该操作。后端默认监听
`http://127.0.0.1:48080`，EMQX Dashboard 默认在 `http://127.0.0.1:18083`。

管理后台数据库种子包含租户 1 的 `admin` 用户，初始密码为 `123456`。仅供首次本地登录；首次进入
后立即修改密码，并为实际租户创建独立管理员，切勿在生产环境保留此账号或密码。

### 启动管理后台

```bash
cd robot-platform-ui-admin
pnpm install --frozen-lockfile
pnpm dev
```

浏览器访问终端输出的本地地址（通常为 `http://127.0.0.1:5173`）。管理端的仪表盘调用
`GET /admin-api/robot/dashboard`，只汇总当前登录租户的数据：机器人、任务趋势使用 MySQL 的持久数据，
在线状态读取 Redis 实时投影；告警功能未接入时会明确显示“告警功能未启用”，不会显示虚构告警数。

### 首次租户与机器人操作

1. 以平台管理员创建租户和租户管理员；以租户管理员登录后，进入“机器人运营”。
2. 创建 Product，按产品录入 Device Inventory；库存创建和编辑仅允许 `super_admin` 且需要相应权限。
3. 激活设备以生成一次性设备凭证。只在受控设备安装流程中展示和保存凭证，绝不复制到工单、日志或前端配置。
4. 使用设备凭证启动 `robot-simulator`，向 EMQX 发送上线 heartbeat；管理端和 APP 端将从各自的受众接口看到状态。
5. 在管理端创建任务并观察 ACK、执行事件和结果。设备重复发送同一消息必须保持幂等；跨租户访问同一机器人应被拒绝。

三个 API 受众使用不同认证和授权边界，不能互换 token：

| 受众 | 前缀 | 身份与用途 |
| --- | --- | --- |
| 管理端 | `/admin-api/**` | 管理员 RBAC，例如租户内机器人、任务和仪表盘 |
| 设备 | `/device-api/**` | 设备认证，例如 heartbeat、设备配置、任务 ACK/事件回传 |
| APP/H5 | `/app-api/**` | 会员会话与机器人绑定授权，例如个人机器人状态 |

### MQTT 与排障

后端本地 profile 从环境变量读取 MySQL、Redis 和 MQTT 配置。修改 `.env` 后重启依赖和后端。设备上线失败时，依次检查：

1. `docker compose ps` 中 mysql、redis、emqx 均为 healthy；`docker compose logs emqx` 不应出现认证拒绝。
2. 设备的 product/device 绑定、激活状态和 MQTT 用户名/secret 与当前租户一致；不要在日志中打印 secret。
3. Redis 可用且 heartbeat 的 MQTT topic、签名时间戳和 nonce 符合 [MQTT boundary](docs/mqtt-boundary.md)。
4. MySQL `robot`、`robot_mission` 仅以租户条件查询；线上数来自 Redis 投影，Redis 故障时读取持久快照。

可运行以下真实 broker 闭环验证。脚本不会输出凭证；它要求 `.env` 中的 Compose/MQTT 变量，并在设置
`RUN_CORE_PLATFORM_API_E2E=true` 后额外验证一个已创建的 API 测试夹具。

```bash
set -a; source .env; set +a
scripts/e2e/core-platform.sh
```

## 验证状态

后端仪表盘单测和真实 Docker/Testcontainers EMQX + Paho 任务闭环是发布前必跑项。管理前端代码可通过已安装依赖的
类型检查与单测验证；但当前仓库存在已知的 Task13 pnpm 锁文件/registry 阻塞：锁文件尚未纳入新增前端测试依赖，且该环境
使用 Node 20.12 与 Vite 8 的 Node 要求不兼容，同时 pnpm 对 registry metadata 请求失败。因此在可由 Node 20.19+ 和正常
registry 访问的环境执行 `pnpm install --frozen-lockfile` 前，不能宣称整仓发布验证全绿。此问题已按用户决定暂缓，Task14
没有修改任何 UI 依赖或锁文件。

## Upstream attribution

This project imports MIT-licensed backend and Vue 3 admin foundations. The pinned revisions, renamed source locations, and instructions for retrieving the original provenance record are in [UPSTREAM.md](UPSTREAM.md).

The upstream MIT [LICENSE](LICENSE) is preserved.


## Digital Human

数字人能力建立在 Realtime Agent 之上：租户可配置数字人形象、Agent、TTS 音色、口型策略、欢迎语、打断和状态动作；设备仍通过 `/device-api/ai/realtime` 使用统一流式协议。V1 提供 STATIC_2D 管理与预览，并为 Live2D/3D renderer 保留供应商无关接口。

设计：`docs/superpowers/specs/2026-09-20-digital-human-design.md`
实施计划：`docs/superpowers/plans/2026-09-20-digital-human.md`
端侧协议：`docs/digital-human-renderer-protocol.md`

验证说明：数字人新增了 schema/service/admin/realtime/UI/permission/E2E contract tests。当前 GitHub 分支未配置可由本连接器触发的 CI run，因此提交记录不把 Maven/Vitest/E2E 标记为已执行；合并前应在标准开发/CI 环境执行 `robot-platform-module-ai` 测试、管理端 type-check/test 和 mock-provider realtime E2E。
