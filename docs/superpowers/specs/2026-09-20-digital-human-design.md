# 数字人（Digital Human）设计规格

日期：2026-09-20
状态：设计基线
适用仓库：`longscoop/ruoyi-robot`
依赖：`docs/superpowers/specs/2026-09-17-realtime-agent-design.md`

## 1. 定位

数字人是 Realtime Agent 的表现层和交互终端，不复制 Agent 的 Prompt、Memory、Model、Skill 或 Tool。一个数字人引用一个 Agent；Agent 仍是对话、记忆和机器人能力执行的唯一业务入口。

目标是让管理端配置“形象 + 声音 + Agent + 动作映射”，Android/H5/机器人屏幕通过供应商无关协议渲染数字人，并复用现有 Realtime Agent 的流式语音、打断、会话、Trace 和 Mission/Tool 边界。

V1 不实现重型 3D 引擎，不把 Live2D/Unity SDK 耦合进 Java 后端，不允许数字人绕过 Agent 直接控制 MQTT/ROS。

## 2. 架构

```text
Admin -> Digital Human Config
                 |
                 v
Android/H5 Digital Human Runtime
  avatar / subtitle / animation / lip-sync
                 |
        WSS Realtime Gateway
                 |
            Agent Runtime
       / Model / Memory / Skill
                 |
          Mission / Robot
```

数字人只负责 presentation。设备仍使用 `/device-api/ai/realtime`；V1 通过 session.start 增加可选 `digitalHumanCode`，服务端解析并校验同租户、启用状态、Agent 绑定关系。

## 3. 数据模型

新增两张核心表。

### ai_digital_human

```text
id
tenant_id
name
code
description
agent_id
avatar_type          STATIC_2D | LIVE2D | THREE_D | EXTERNAL
avatar_url
avatar_resource_url
cover_url
voice_model_id       nullable; 必须为 TTS 模型
voice_id
speech_rate
pitch
volume
lip_sync_mode        AUDIO_LEVEL | VISEME | PROVIDER
welcome_text
interrupt_enabled
config_json
status
created_at
updated_at
```

约束：code 在租户内唯一；agent/model 必须同租户；读取配置不得返回 Provider secret；资源 URL 仅是受控资源引用，不能成为任意脚本执行入口。

### ai_digital_human_action

```text
id
tenant_id
digital_human_id
state                IDLE | LISTENING | THINKING | SPEAKING | EXECUTING | ERROR
action_code
config_json
created_at
updated_at
```

同一数字人每个 state 最多一个有效动作映射。

## 4. Agent 与声音边界

- `agent_id` 决定 Prompt、模型路由、Memory、Tool/Skill。
- 数字人的 `voice_model_id/voice_id` 是 presentation override；为空时使用 Agent 的 TTS/voice 配置。
- NATIVE S2S 若 Provider 不支持指定数字人音色，则使用实际 Provider 能力并在 session trace 记录 resolved voice。
- Digital Human 不保存 API Key、baseUrl 或 Provider credential。

## 5. 状态机

标准 UI 状态：

```text
IDLE -> LISTENING -> THINKING -> SPEAKING -> IDLE
                         |
                         +-> EXECUTING -> THINKING/SPEAKING
any -> ERROR
SPEAKING --barge-in--> LISTENING
```

状态来自服务端事件和已有 realtime 事件确定性映射，客户端不得通过定时器猜测业务状态。

## 6. 协议扩展

session.start 可增加：

```json
{"type":"session.start","agentCode":"xiaoyou","digitalHumanCode":"xiaoyou-avatar","clientCapabilities":{"viseme":true,"audioLevelLipSync":true}}
```

新增事件：

```text
digital_human.config
digital_human.state
digital_human.viseme
```

所有事件沿用 sessionId、turnId、sequence、serverTime。已有 assistant.text.delta / assistant.audio.* / playback.stop 继续复用，不复制一套数字人音频协议。

`digital_human.config` 返回当前会话解析后的安全配置快照；不包含 secret。
`digital_human.state` 包含 state/actionCode。
`digital_human.viseme` 仅在实际 TTS/Provider 能提供可靠时间轴时发送；否则客户端使用 AUDIO_LEVEL。

## 7. 管理 API

```text
GET    /admin-api/ai/digital-humans
GET    /admin-api/ai/digital-humans/{id}
POST   /admin-api/ai/digital-humans
PUT    /admin-api/ai/digital-humans/{id}
DELETE /admin-api/ai/digital-humans/{id}
PUT    /admin-api/ai/digital-humans/{id}/actions
POST   /admin-api/ai/digital-humans/{id}/preview-session
```

所有 ID 查询必须同时限定 tenant_id。preview-session 只生成预览所需安全配置/短期会话，不授予额外 Robot Skill 权限。

## 8. 管理后台

AI 中心新增“数字人”页面，Realtime Agent 原六页不重构。

页面包含：列表、基础信息、Agent 绑定、形象资源、声音、状态动作、欢迎语、打断开关和预览。

预览必须调用真实后端配置，不使用伪造业务数据。第一版预览可用 STATIC_2D + CSS/简单动画 + 字幕 + 流式音频；Live2D/3D 作为后续 renderer adapter。

## 9. Android/H5 Runtime

定义供应商无关 renderer：

```text
DigitalHumanRenderer
  load(config)
  setState(state, action)
  pushViseme(timeline)
  pushAudioLevel(level)
  reset()
```

V1 必须支持 STATIC_2D；协议和接口为 LIVE2D/THREE_D 预留，但不能为了未来能力阻塞 V1。

机器人端继续只理解 ruoyi-robot 平台协议，不接触 Qwen/Doubao/DeepSeek 数字人或语音私有协议。

## 10. 安全与多租户

- tenant 来自认证上下文，忽略客户端 tenantId。
- DigitalHuman -> Agent -> Model 全链路校验 tenant。
- 数字人不能扩大 Agent/Member/Robot 的授权范围。
- config_json 做 schema/字段白名单校验，禁止脚本、任意 HTML 和任意可执行 URL。
- 日志不记录 Provider secret、设备 secret 或原始家庭音频。
- 跨租户读取、修改、预览必须拒绝。

## 11. 可观测性

ai_realtime_session 的 metadata/trace 记录：digitalHumanId、avatarType、lipSyncMode、resolvedVoiceModelId、resolvedVoiceId；不为这些字段新增重复会话表。

关键指标：配置解析失败率、首音频延迟、状态事件延迟、viseme 可用率、打断后 playback.stop 延迟。

## 12. V1 验收

- 租户管理员可 CRUD 数字人并绑定同租户 Agent。
- 可配置 STATIC_2D、声音、欢迎语、状态动作和打断。
- 设备指定 digitalHumanCode 后得到安全配置快照与统一状态事件。
- 流式文本/音频、打断继续复用 Realtime Agent。
- STATIC_2D 预览可随 LISTENING/THINKING/SPEAKING/EXECUTING 切换。
- 跨租户 Agent/数字人/模型绑定均失败。
- 数字人不能绕过 Agent -> Skill/Tool -> Mission。
- Provider secret 不进入数字人 API、前端状态或日志。

## 13. 后续

V2：Live2D renderer、viseme 精准口型、情绪/表情、资源包版本管理。
V3：3D/Unity renderer、导览大屏、多模态人物动作、数字人模板市场。
