# 🤖 RuoYi Robot

> 开源的机器人运营与 AI Agent 平台，让机器人快速拥有云端管理、设备接入、任务调度、AI 智能体与数字人能力。

**RuoYi Robot** 是一个面向机器人、具身智能和 AI 应用的开源平台，基于 **RuoYi-Vue-Pro** 二次开发。

项目将传统 IoT 设备管理、机器人运营平台与 AI Agent 能力结合，为家庭机器人、服务机器人、巡检机器人、陪伴机器人以及机器人开发团队提供统一的云端基础设施。

平台支持机器人设备接入、MQTT 通信、在线状态、任务下发、机器人绑定、多租户运营，并提供大模型、Prompt、Agent、知识库、Realtime Agent、长期记忆和数字人等 AI 能力。

其中知识库目前提供管理基础，完整 RAG 尚在建设中；长期记忆与 LiveTalking / WebRTC 已有代码实现，实机与生产验证范围见下方能力状态。

✨ 为什么做 RuoYi Robot

开发一款真正可运营的机器人产品，除了 ROS、导航、视觉、语音等机器人端能力，还需要大量云端基础设施：

- 机器人设备注册、激活和认证
- MQTT 长连接与实时状态
- 机器人任务下发和执行结果回传
- 用户与机器人绑定
- 多租户与权限管理
- OTA 与设备运维
- AI 大模型接入
- Agent 与机器人能力调用
- 知识库和长期记忆
- 实时语音交互
- 数字人
- Web / APP / H5 接入

RuoYi Robot 希望把这些通用能力沉淀为一个可复用的开源平台。

机器人团队可以把更多精力放在机器人本身，而不是重复开发账号、权限、设备管理、MQTT、任务系统和 AI 基础设施。

# 🏗️ 系统架构

![architecture-diagram](./docs/images/architecture-diagram.png)

# 🔐 API 架构

RuoYi Robot 根据调用方划分三套 API 安全边界：

| 调用方         | API              | 用途                                       |
| -------------- | ---------------- | ------------------------------------------ |
| 管理后台       | `/admin-api/**`  | RBAC、机器人、设备、任务、AI、运营管理     |
| Robot / Device | `/device-api/**` | Heartbeat、配置、任务 ACK、事件和结果      |
| APP / H5       | `/app-api/**`    | 用户登录、机器人绑定、机器人状态与用户操作 |

三种 API 使用不同身份认证和授权边界，Token 不可互换。

# 🚀 核心能力

## 能力状态

以下状态依据仓库现有代码、配置和测试区分：**代码已实现**表示已有对应实现；**待实机/生产验证**表示真实部署效果仍需确认；**建设中**表示已有部分基础但完整链路尚未完成；**计划**表示尚未提供对应完整实现。代码实现与部署验证是两个维度，自动化回归不等于生产验证。

| 能力 | 状态 | 当前范围与验证边界 |
| --- | --- | --- |
| 长期记忆 / 对话记忆 | 代码已实现 | 已有 MySQL 长期记忆、本地摘要、Mem0、PowerMem 和关闭模式；包含召回筛选、上下文注入及相关回归测试。远程服务适配测试不代表目标环境的真实服务联调或生产验证完成。 |
| Realtime Voice Agent | 代码已实现 | 已有 NATIVE、CASCADE、AUTO 路由，以及 ASR → LLM → TTS 级联实现和相关回归测试；实际设备、麦克风与播放效果需在部署环境确认。 |
| STATIC_2D 数字人 | 代码已实现 | 已有内置形象、状态动作和音量驱动的口型表现；不代表逐音素口型同步。 |
| LiveTalking / WebRTC 数字人 | 代码已实现；待实机/生产验证 | 已有服务实例与形象选择、SDP 协商、回答音频转发、打断及浏览器连接管理；真实 GPU 推理、模型素材、口型同步和跨网段 WebRTC 仍待部署验证。 |
| 知识库管理 | 代码已实现 | 已有知识库、纯文本知识文档管理及 Agent 关联，不等于完整 RAG。 |
| 完整 RAG | 建设中 | 基于已有知识库管理，文件上传解析、分块、Embedding、向量数据库与语义检索链路尚未完成。 |
| Live2D、3D Avatar、第三方 Renderer 扩展 | 计划 | 已预留统一 Renderer 接口，不表示这些渲染后端已经实现。 |

实现与回归依据：[记忆 Provider](robot-platform-module-ai/src/main/java/com/robot/platform/ai/memory/provider/)、[记忆测试](robot-platform-module-ai/src/test/java/com/robot/platform/ai/memory/)、[实时语音运行时](robot-platform-module-ai/src/main/java/com/robot/platform/ai/realtime/runtime/)、[实时语音测试](robot-platform-module-ai/src/test/java/com/robot/platform/ai/realtime/runtime/)、[知识库服务](robot-platform-module-ai/src/main/java/com/robot/platform/ai/knowledge/service/AiKnowledgeService.java)。LiveTalking 的验证边界见下方数字人说明。

## 🤖 机器人运营

提供机器人从设备入库、激活、上线到运营管理的完整基础能力。

主要包括：

- Product 产品管理
- Device Inventory 设备库存
- 机器人激活
- 设备凭证管理
- 机器人在线 / 离线状态
- 电量、版本等状态信息
- 机器人详情
- 用户与机器人绑定
- 多租户机器人隔离
- 机器人配额管理
- 机器人运营 Dashboard

## 📡 设备与 MQTT

平台内置 EMQX 集成，通过 MQTT 实现机器人与云端之间的实时通信。

支持：

- 设备身份认证
- MQTT 设备连接
- Heartbeat 心跳
- 在线状态维护
- 状态上报
- 云端指令下发
- ACK
- 任务执行事件
- 任务结果回传
- 消息幂等
- 租户隔离
- Redis 实时状态投影

机器人可以使用：

```
Robot
  │
  ├── Heartbeat
  ├── Telemetry
  ├── Mission ACK
  ├── Mission Event
  └── Mission Result
  │
 MQTT
  │
  ▼
RuoYi Robot Cloud
```

完成机器人和云平台之间的双向通信。

## 📋 机器人任务

平台提供统一机器人任务模型，可用于：

- 移动
- 导航
- 做家务
- 巡检
- 拍照
- 视频
- 找人
- 找物
- 回充
- 自定义机器人技能

典型任务链路：

```
创建任务
   ↓
云端下发
   ↓
MQTT
   ↓
机器人 ACK
   ↓
机器人执行
   ↓
执行事件
   ↓
任务结果
   ↓
云端持久化
```

为后续 Robot Agent 自动调用机器人能力提供统一任务基础。

# 🧠 AI 大模型与 Robot Agent

RuoYi Robot 不仅是机器人设备管理平台，同时提供 AI Agent 基础能力。

平台支持统一管理：

- AI 服务商
- 大模型
- Prompt
- Agent
- Robot ↔ Agent 绑定
- AI 对话
- Realtime Agent
- 长期记忆
- 知识库
- 数字人
- TTS

`CHAT` 模型可接入 Coze、Dify、FastGPT 和通义千问。AI 工作流通过云平台受保护的任务接口操作机器人。

小智 ESP32-S3 可通过独立协议适配器接入现有 Realtime Agent，进行麦克风输入和扬声器回复测试，适配器位于 `script/xiaozhi-bridge/`，以 `config.example.json` 为模板配置设备身份及服务地址。

机器人可以绑定不同 Agent，从传统的：

```
用户 → APP → API → 机器人任务
```

逐渐演进为：

```
用户
 │
 ▼
AI Agent
 │
 ├── LLM
 ├── Knowledge Base
 ├── Memory
 ├── Tools
 └── Robot Skills
        │
        ▼
     Mission
        │
       MQTT
        │
        ▼
      Robot
```



从而让机器人从“接受固定指令”升级为“理解任务并执行任务”。

# 📚 知识库

平台已经提供知识库和纯文本知识文档管理基础能力，可与 Agent 建立关联。

规划中的完整 RAG 链路：

```
PDF / Word / Markdown / TXT
            ↓
        文档解析
            ↓
          Chunk
            ↓
        Embedding
            ↓
       Vector Store
            ↓
         Retrieval
            ↓
           LLM
            ↓
          Agent
```

> 当前版本已经支持知识库和纯文本知识文档管理；文件上传解析、Embedding、向量数据库和语义检索仍在持续建设中。

# 🎭 Digital Human

RuoYi Robot 提供建立在 **Realtime Agent** 之上的数字人能力。

数字人可以配置：

- 数字人形象
- Agent
- TTS 模型 / 音色
- 欢迎语
- 打断策略
- 口型策略
- 状态动作

当前支持可切换的渲染后端：

```
STATIC_2D
LIVETALKING（WebRTC 实时音视频）
```

LiveTalking 接入代码已实现服务实例和形象选择、WebRTC SDP 协商、实时回答音频转发及打断。可配置不同的外部实例接入 Wav2Lip、MuseTalk 等模型，实际可用性取决于对应服务与模型素材的部署。管理端已提供视频连接和试听入口；服务端通过 `ROBOT_LIVETALKING_ENABLED`、`ROBOT_LIVETALKING_URL` 和 `ROBOT_LIVETALKING_TOKEN` 配置服务，管理端选择对应服务实例和形象 ID。旧配置默认使用静态形象，无需数据库迁移。

> **待实机/生产验证：**现有自动化测试使用本地模拟 HTTP 服务和模拟浏览器 PeerConnection，覆盖协议、音频转发、打断与资源释放，不覆盖真实 GPU 推理、模型素材、口型同步或跨网段 WebRTC。上述项目仍需实际部署验证，不能将“代码已支持”理解为“生产实机已验证”。

验证依据：[后端 Provider 测试](robot-platform-module-ai/src/test/java/com/robot/platform/ai/digitalhuman/provider/DigitalHumanProviderTest.java)、[前端 Renderer 测试](robot-platform-ui-admin/src/views/ai/digital-human/runtime/LiveTalkingRenderer.spec.ts)、[服务配置](robot-platform-server/src/main/resources/application.yaml)，以及 [490f508 中的实现与验证记录](https://github.com/longscoop/ruoyi-robot/blob/490f508ccc54c0c284b3d1a77dee57223a04d07e/docs/digital-human-livetalking.md)（历史文档，当前主干已移除）。

计划扩展以下渲染后端，目前仅预留统一 Renderer 接口：

```
Live2D
3D Avatar
Third-party Renderer
```

整体链路：

```
Microphone
    ↓
Realtime Agent
    ↓
   LLM
    ↓
   TTS
    ↓
Digital Human Renderer
    ↓
Avatar + Voice + Lip Sync
```



# 🧱 技术栈

## Backend

| 技术           | 用途                   |
| -------------- | ---------------------- |
| Java 17        | 后端开发语言           |
| Spring Boot    | 应用框架               |
| RuoYi-Vue-Pro  | 基础后台框架           |
| Maven          | Java 工程管理          |
| MySQL 8.4      | 业务数据               |
| Redis 7        | 缓存、实时机器人状态   |
| EMQX 5.8       | MQTT Broker            |
| MQTT           | Robot ↔ Cloud 实时通信 |
| Docker Compose | 本地基础设施部署       |

后端采用 **Modular Monolith（模块化单体）** 架构，主要模块使用：

```
robot-platform-*
```

Java package：

```
com.robot.platform
```

## Frontend

| 技术       | 用途         |
| ---------- | ------------ |
| Vue 3      | 管理后台     |
| TypeScript | 前端开发语言 |
| Vite       | 构建工具     |
| pnpm       | 包管理       |

管理后台：

```
robot-platform-ui-admin
```

## AI

AI 层主要围绕：

```
LLM Provider
      ↓
Model
      ↓
Prompt
      ↓
Agent
      ↓
Knowledge / Memory / Tools
      ↓
Robot Skill
```

设计。

可用于接入不同的大模型服务和机器人能力。

# 📦 项目结构

```
ruoyi-robot
│
├── robot-platform-server
│
├── robot-platform-framework
│
├── robot-platform-dependencies
│
├── robot-platform-module-system
│
├── robot-platform-module-infra
│
├── robot-platform-module-tenant
│
├── robot-platform-module-member
│
├── robot-platform-module-device
│
├── robot-platform-module-robot
│
├── robot-platform-ui-admin
│
├── robot-simulator
│
├── docker
│
├── scripts
│
├── sql
│
└── docs
```

# 🖼️ 功能演示

> 建议将截图统一放到 `docs/images/`。

## Dashboard



机器人数量、在线状态、任务趋势等运营数据。

![image-20260928131620448](./docs/images/image-20260928131620448.png)



# 🚀 Quick Start

## 环境要求

建议：

```
JDK       17+
Maven     3.9+
Docker    Docker Compose v2
MySQL     8.4
Redis     7
EMQX      5.8
Node.js   20.19+ / 22.12+
pnpm      9+
```

## 1. Clone

```
git clone https://github.com/longscoop/ruoyi-robot.git
cd ruoyi-robot
```

## 2. 配置环境变量

在项目根目录创建：

```
.env
```

至少配置：

```
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD

EMQX_NODE_COOKIE
EMQX_DASHBOARD_PASSWORD

MQTT_CALLBACK_TOKEN
MQTT_CLOUD_USERNAME
MQTT_CLOUD_PASSWORD

ROBOT_SECRET_MASTER_KEY
ROBOT_AI_SECRET_KEY_BASE64
```

设备凭证和 AI 凭证分别使用独立 32 字节随机密钥，例如：

```
openssl rand -base64 32
```

> 首次配置后请妥善保存密钥，不要随意更换，也不要将 `.env`、设备 Secret、MQTT Secret 或 AI Provider Secret 提交到 Git。

## 3. 启动基础设施

```
docker compose up -d --wait mysql redis emqx
```

默认启动：

```
MySQL
Redis
EMQX
```

EMQX Dashboard：

```
http://127.0.0.1:18083
```

## 4. 启动 Backend

```
set -a
source .env
set +a

mvn -pl robot-platform-server -am -DskipTests package

java -jar robot-platform-server/target/robot-platform-server.jar \
  --spring.profiles.active=local
```

默认地址：

```
http://127.0.0.1:48080
```

首次启动时 Docker Compose 会初始化：

```
sql/mysql/ruoyi-vue-pro.sql
sql/mysql/quartz.sql
sql/mysql/robot-platform.sql
```

## 5. 启动管理后台

```
cd robot-platform-ui-admin

pnpm install --frozen-lockfile
pnpm dev
```

默认：

```
http://localhost:80
```

## 6. 登录

本地开发环境默认管理员：

```
Tenant:   RuoYi Robot
Username: admin
Password: admin123
```

> 该账号仅用于本地首次启动。生产环境必须修改默认密码并创建独立管理员。

# 🤖 接入第一台机器人

推荐流程：

```
创建 Tenant
     ↓
创建 Product
     ↓
创建设备
     ↓
激活 Device
     ↓
获取 Device Credential
     ↓
Robot 连接 MQTT
     ↓
发送 Heartbeat
     ↓
Robot Online
     ↓
Cloud 创建 Mission
     ↓
Robot ACK
     ↓
Robot Execute
     ↓
Result
```

可以先使用项目自带：

```
robot-simulator
```

模拟真实机器人完成整个 MQTT 链路。

# 🧪 E2E 验证

项目提供真实 MQTT Broker 闭环验证：

```
set -a
source .env
set +a

scripts/e2e/core-platform.sh
```

验证：

```
Device
  ↓
EMQX
  ↓
Cloud
  ↓
Mission
  ↓
ACK
  ↓
Event
  ↓
Result
```

整个核心链路。

# 🗺️ Roadmap

RuoYi Robot 将继续围绕 **Robot Cloud + Robot Agent** 演进。

**建设中：**完整 RAG。在已有知识库与纯文本知识文档管理基础上，补齐文件上传解析、分块、Embedding、Vector Database 和语义检索。

**待实机/生产验证：**LiveTalking 的真实 GPU 推理、模型素材、口型同步和跨网段 WebRTC，以及真实设备上的音视频交互效果。

长期记忆、Realtime Voice Agent 和 LiveTalking / WebRTC 的现有实现已列入上方能力状态，不再作为尚未实现的计划重复列出。

**计划：**

- Robot Python SDK
- ROS1 SDK / Bridge
- ROS2 SDK / Bridge
- OTA
- Robot Log Center
- Remote Diagnostics
- Remote Control
- Map Management
- Robot Skill / Capability
- Agent Tool Calling
- Robot Workflow
- Live2D Digital Human
- 3D Digital Human
- Third-party Renderer 扩展
- APP / H5 Robot Console

最终希望形成：

```
              RuoYi Robot

       Robot Cloud Platform
                +
          Robot AI Agent
                +
      Robot Developer Platform
```

对话记忆代码已实现 MySQL 长期记忆（`LONG_TERM`）、本地摘要（`MEM_LOCAL_SHORT`）、Mem0（`MEM0AI`）、PowerMem（`POWERMEM`）和关闭模式，可在智能体配置中切换。服务端配置位于 `application.yaml` 的 `robot.ai.memory`，远程服务凭据通过环境变量提供；PowerMem 适配服务启动说明见 [README](script/powermem-service/README.md)。具体服务在目标环境的可用性仍需联调确认。

已有数据库升级时，执行 `sql/mysql/ai-admin-display-20261001.sql` 更新角色修改权限及对话菜单；新环境使用 `sql/mysql/robot-platform.sql` 初始化。
