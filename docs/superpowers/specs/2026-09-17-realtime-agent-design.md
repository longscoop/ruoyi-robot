# 实时语音智能体设计规格

日期：2026-09-17  
状态：已确认设计，待实施计划  
适用仓库：`longscoop/ruoyi-robot`

## 1. 背景与目标

在现有多租户机器人管理平台上新增实时对话智能体能力，使租户可以定义角色/智能体、配置提示词和模型，并让 RK3588 主控通过统一实时协议完成低延迟语音对话、打断、工具调用和长期记忆。

V1 目标：

- 管理端可创建“角色/智能体”，配置 System Prompt、聊天模型、Realtime 模型、ASR、TTS 和记忆策略。
- 首批接入 Qwen、DeepSeek、Doubao。
- Qwen、Doubao 支持原生实时语音链路；DeepSeek 通过 `ASR -> LLM -> TTS` 级联链路使用。
- RK3588 只实现平台定义的 Realtime Agent Protocol，不依赖任何模型供应商协议。
- 支持连续语音、多轮上下文、边生成边播放和用户打断。
- 支持 SESSION、MEMBER、MEMBER_ROBOT、ROBOT 四类记忆范围，防止跨租户、跨成员串记忆。
- Agent 的机器人能力调用必须经过 Skill / Tool / Mission 边界，禁止 LLM 直接发布 MQTT、ROS Topic 或直接修改业务数据库。
- 会话、模型调用、工具调用和错误可追踪，能够回答“机器人为什么这样回复或执行动作”。

V1 非目标：

- 不在平台实现 3D 数字人渲染引擎。
- 不提供任意 Shell、任意 SQL、任意 MQTT Topic Publish 给 Agent。
- 不要求第一版实现知识图谱或复杂记忆图谱。
- 不默认长期保存家庭原始语音。
- 不要求所有供应商具备完全相同的模型能力；通过能力抽象处理差异。

## 2. 现有架构约束

本功能必须沿用仓库现有架构，而不是引入第二套平台：

- Java 17、Spring Boot、MyBatis Plus、MySQL、Redis、Vue 3。
- `/admin-api/**`、`/device-api/**`、`/app-api/**` 三类受众继续保持独立认证边界。
- 租户、成员、机器人、缓存、消息、会话、模型配置和记忆均必须带租户隔离。
- 机器人控制继续复用现有 Robot/Mission/MQTT 能力；Realtime 音频不走 MQTT。
- `member_robot_binding` 继续作为成员访问机器人及成员-机器人记忆授权的重要边界。
- Agent 与 Robot 保持多对多绑定，不在 `robot` 表增加单一 `agent_id`。

## 3. 总体架构

采用“统一 Realtime Gateway + Native/Cascade 双通道”架构：

```text
RK3588
麦克风 / 扬声器 / 本地 VAD / 声纹或人脸识别
        |
        | WSS + Binary Audio
        v
Realtime Gateway
        |
        v
Agent Runtime
  |       |        |
Prompt  Memory   Skill/Tool
  |       |        |
  +-------+--------+
          |
      Model Router
      /          \
 Native S2S     Cascade
   |              |
 Qwen           ASR
 Doubao           |
                  v
                Chat LLM
                  |
                  v
                 TTS
```

关键原则：

1. RK3588 不直接理解 Qwen、Doubao、DeepSeek 的协议。
2. 模型供应商协议只存在于服务端 Adapter 内部。
3. Realtime Gateway 负责设备长连接、会话生命周期、音频帧和控制事件。
4. Agent Runtime 负责角色、上下文、记忆、工具调用和模型路由。
5. Model Router 根据 Agent 配置和模型能力选择 Native 或 Cascade。
6. 机器人动作必须通过 Skill / Tool / Mission 进入现有机器人业务链路。

## 4. Maven 模块与包结构

启用现有规划中的：

```text
robot-platform-module-ai
```

包结构：

```text
com.robot.platform.ai
├── agent
├── prompt
├── model
│   ├── provider
│   ├── chat
│   ├── realtime
│   ├── asr
│   ├── tts
│   └── embedding
├── realtime
├── conversation
├── memory
├── skill
└── tool
```

不得新增独立 Maven Module：

```text
robot-agent
robot-memory
robot-realtime
```

这些属于 AI 领域内部包。

## 5. Agent / 角色模型

管理后台展示名称使用“智能体/角色”，服务端领域统一使用 `Agent`。不单独增加 `ai_role`。

`ai_agent` 核心字段：

```text
id
 tenant_id
name
code
description
system_prompt_id
conversation_model_id
realtime_model_id
asr_model_id
tts_model_id
realtime_mode          NATIVE | CASCADE | AUTO
memory_mode            NONE | SESSION | LONG_TERM
memory_read_enabled
memory_write_enabled
knowledge_enabled
voice_config_json
status
created_at
updated_at
```

说明：

- `conversation_model_id`：普通文本或级联语音场景使用的聊天模型。
- `realtime_model_id`：原生 Speech-to-Speech 模型。
- `asr_model_id` / `tts_model_id`：Cascade 模式使用，可由不同供应商组合。
- Agent 只引用模型记录，不直接保存 `baseUrl`、`apiKey`。
- System Prompt 引用独立 Prompt 记录，支持版本化和回滚。

## 6. Agent 与 Robot 绑定

新增：

```text
ai_agent_robot
--------------
id
tenant_id
agent_id
robot_id
is_default
status
created_at
updated_at
```

约束：

- 一个 Agent 可以绑定多个 Robot。
- 一个 Robot 可以绑定多个 Agent。
- 同一 Robot 同时只能有一个有效默认 Agent。
- 所有查询和写入必须同时约束 `tenant_id`。

## 7. 模型与 Provider 抽象

核心客户端接口：

```java
ChatModelClient
RealtimeVoiceClient
AsrClient
TtsClient
EmbeddingClient
```

通过统一注册中心按模型配置解析实现：

```java
ModelClientRegistry
```

Provider Adapter：

```text
Qwen
├── QwenChatModelClient
├── QwenRealtimeVoiceClient
├── QwenAsrClient
└── QwenTtsClient

Doubao
├── DoubaoChatModelClient
├── DoubaoRealtimeVoiceClient
├── DoubaoAsrClient
└── DoubaoTtsClient

DeepSeek
└── DeepSeekChatModelClient
```

上层业务不得散落 `if (provider == ...)`。供应商差异必须封装在 Adapter/Capability 层。

## 8. 模型数据结构

`ai_model_provider`：

```text
id
tenant_id
name
code
provider_type          QWEN | DEEPSEEK | DOUBAO
base_url
api_key_ciphertext
config_json
status
created_at
updated_at
```

`ai_model`：

```text
id
tenant_id
provider_id
name
model_code
model_type             CHAT | REALTIME_S2S | ASR | TTS | EMBEDDING
capabilities_json
config_json
status
created_at
updated_at
```

安全要求：

- API Key 加密存储。
- 管理端读取时永不返回 Key 明文。
- 应用日志、异常和 Trace 不打印 Key。
- 前端不能直接访问模型供应商凭证。

## 9. Realtime 模式

### 9.1 NATIVE

适用于供应商提供原生 Speech-to-Speech Realtime 的模型。

```text
Audio In -> Native Realtime Model -> Audio/Text Out
```

V1 目标 Provider：Qwen、Doubao。

### 9.2 CASCADE

适用于 DeepSeek 或需要自行组合模型的场景：

```text
Audio In -> ASR -> ChatModel -> TTS -> Audio Out
```

允许组合：

```text
Qwen ASR -> DeepSeek Chat -> Doubao TTS
```

### 9.3 AUTO

AUTO 是路由策略，不是第三类 Provider。

默认策略：

- 普通低延迟闲聊优先 Native。
- 需要指定 Chat 模型、复杂文本推理或 Native 模型能力不足时切换 Cascade。
- 工具调用是否触发 Cascade 由 Provider capability 和 Agent policy 决定。
- 路由结果记录到会话 Trace，便于审计。

V1 AUTO 不做不可解释的机器学习路由，采用确定性规则。

## 10. Realtime Gateway

设备入口：

```text
WSS /device-api/ai/realtime
```

设备使用现有设备身份完成握手认证；服务端从认证结果解析租户和机器人身份，不信任客户端自行声明的 `tenantId`。

### 10.1 Session Start

客户端发送 JSON Text Frame：

```json
{
  "type": "session.start",
  "agentCode": "xiaoyou",
  "identity": {
    "memberId": 20003,
    "type": "VOICEPRINT",
    "confidence": 0.94
  },
  "audio": {
    "codec": "PCM_S16LE",
    "sampleRate": 16000,
    "channels": 1
  }
}
```

服务端校验：

- 当前设备是否属于认证租户。
- Agent 是否属于同租户且绑定当前 Robot。
- `memberId` 是否通过当前租户下的成员-机器人授权边界。
- 身份置信度是否达到可写 MEMBER Memory 的策略阈值。

无法确认成员身份时，服务端把会话降级为匿名会话，而不是猜测成员。

### 10.2 音频传输

音频使用 WebSocket Binary Frame，禁止每帧 Base64 包入 JSON。

控制、状态、文本增量使用 JSON Text Frame。

### 10.3 服务端事件

建议 V1 事件集：

```text
session.created
session.error
input.speech_started
input.speech_stopped
input.transcript.delta
input.transcript.done
assistant.text.delta
assistant.text.done
assistant.audio.started
assistant.audio.done
assistant.interrupted
assistant.done
playback.stop
tool.started
tool.done
session.closed
```

每个事件至少带：

```text
sessionId
turnId
sequence
serverTime
```

需要幂等的控制事件还应带 `eventId`。

## 11. 打断与半双工/全双工策略

目标：用户在机器人播报时开口，机器人尽快停止当前播报并开始新一轮。

优先级：

1. RK3588 本地 VAD 检测到用户开始讲话。
2. 立即发送 `input.speech_started`。
3. 本地可以先停止或淡出当前播放，避免等待公网往返。
4. 服务端取消当前 turn 的模型生成或丢弃后续音频。
5. 服务端发送 `playback.stop` / `assistant.interrupted`，完成状态对齐。
6. 新一轮音频继续上传。

本地 VAD 是低延迟打断入口；云端 VAD/Provider VAD 是辅助，不作为唯一打断依据。

## 12. Conversation 与 Trace

`ai_conversation`：

```text
id
tenant_id
agent_id
robot_id
member_id
channel               ROBOT_VOICE | APP_TEXT | APP_VOICE | WEB
status
started_at
ended_at
created_at
updated_at
```

`ai_conversation_message`：

```text
id
tenant_id
conversation_id
turn_id
role                  SYSTEM | USER | ASSISTANT | TOOL_CALL | TOOL_RESULT
content
model_id
input_tokens
output_tokens
latency_ms
metadata_json
created_at
```

`metadata_json` 可存放不值得独立成列、但需要审计的 Provider request/session id、finish reason、ASR/TTS 延迟等信息；不得存供应商密钥。

默认不保存原始家庭语音。默认持久化：

- ASR 文本。
- Assistant 文本。
- Tool/Skill 调用参数与结果摘要。
- 模型、耗时和错误信息。

未来需要录音时必须以独立配置显式开启，并定义留存周期和权限。

## 13. Realtime Session 运行记录

新增：

```text
ai_realtime_session
-------------------
id
tenant_id
conversation_id
agent_id
robot_id
member_id
mode                  NATIVE | CASCADE
provider_id
model_id
provider_session_id
connected_at
first_audio_at
first_response_at
ended_at
interrupt_count
error_code
status
created_at
updated_at
```

该表用于统计：

- 连接成功率。
- 首文本延迟。
- 首音频延迟。
- 平均会话时长。
- 打断次数。
- Provider/Model 错误率。

## 14. 身份与记忆授权

RK3588 可以利用现有声纹、人脸能力提供候选成员身份：

```text
memberId
identityType          VOICEPRINT | FACE | APP_BOUND | ANONYMOUS
identityConfidence
```

服务端永远再次校验成员与机器人绑定关系。

匿名或低置信度场景：

- 可使用 SESSION Memory。
- 可读取允许匿名读取的 Robot 公共记忆。
- 不写 MEMBER Memory。
- 不写 MEMBER_ROBOT Memory。

不允许因为“听起来像某个人”而跨成员写长期记忆。

## 15. Memory 模型

记忆范围：

```text
SESSION
MEMBER
MEMBER_ROBOT
ROBOT
```

含义：

- SESSION：当前会话临时上下文，不作为长期事实。
- MEMBER：与成员本身有关的长期偏好、资料和习惯。
- MEMBER_ROBOT：特定成员和特定机器人之间形成的交互偏好或上下文。
- ROBOT：机器人/家庭环境相关、可被授权角色复用的事实。

长期记忆核心表：

```text
ai_memory
---------
id
tenant_id
scope                 MEMBER | MEMBER_ROBOT | ROBOT
member_id
robot_id
memory_type           PROFILE | PREFERENCE | RELATION | HABIT | FACT | ENVIRONMENT | INSTRUCTION
content
summary
importance
confidence
source_conversation_id
source_message_id
first_observed_at
last_observed_at
expires_at
status                ACTIVE | SUPERSEDED | DELETED
created_at
updated_at
```

约束：

- `MEMBER` 必须有 `member_id`。
- `MEMBER_ROBOT` 必须同时有 `member_id` 和 `robot_id`。
- `ROBOT` 必须有 `robot_id`。
- 所有记录必须有 `tenant_id`。
- 逻辑删除/失效记录不能参与召回。

## 16. Memory Pipeline

实时回答链路和记忆提炼链路解耦：

```text
USER/ASSISTANT Turn
       |
       +------> Realtime Response
       |
       +------> Memory Candidate
                    |
               Extractor
                    |
                 Policy
                    |
          Deduplicate / Merge
                    |
                ai_memory
```

记忆提炼不得阻塞实时回答。

V1 自动记忆范围：

- 稳定个人信息。
- 用户明确偏好。
- 家庭关系。
- 稳定习惯。
- 用户明确要求“记住”的内容。
- 有长期价值的机器人/家庭环境事实。
- 具有明确时效的事实可写入，但必须带 `expires_at`。

V1 默认不长期记录：

- 寒暄。
- 笑话、临时闲聊。
- 一次性无后续价值的问答。
- 无法确认所属成员的个人事实。

明确命令：

- “记住……”：提高候选优先级并立即进行提炼。
- “别记这个”：禁止当前内容进入长期记忆。
- “忘掉……”：定位对应记忆并失效/软删除。
- 成员关闭长期记忆：后续不写该成员的 MEMBER / MEMBER_ROBOT Memory。

## 17. Memory 召回

V1 召回过滤顺序必须先做权限边界，再做相关性：

```text
tenant_id
 -> member_id / robot_id / scope
 -> status / expires_at
 -> 相关性
 -> importance
 -> recentness
 -> Top K
```

严禁先做全库向量搜索再在结果层过滤租户。

V1 可先使用 MySQL 文本、类型、重要度和时间进行召回，同时定义：

```java
MemoryStore
MemoryRetriever
MemoryExtractor
MemoryPolicy
```

后续切换 pgvector、Milvus 或其他向量检索时不改变 Agent Runtime 接口。

## 18. Prompt 管理

新增：

```text
ai_prompt
---------
id
tenant_id
name
code
type                  SYSTEM | MEMORY_EXTRACT | MEMORY_SUMMARY | TOOL_ROUTING
content
version
status
created_at
updated_at
```

Agent 通过 `system_prompt_id` 绑定系统 Prompt。

Prompt 版本必须可追踪。Conversation/Trace 中记录实际使用的 Prompt 版本，避免后台修改 Prompt 后无法解释历史行为。

## 19. Skill / Tool / Mission 边界

必须保持：

```text
Agent
  -> Skill
      -> Tool / Mission
          -> Robot Command / MQTT
```

禁止：

```text
LLM -> MQTT
LLM -> ROS Topic
LLM -> 任意 HTTP
LLM -> 任意 SQL
LLM -> 业务数据库直接更新
```

每个 Skill/Tool 必须声明参数 Schema 和风险级别。

风险控制由服务端策略执行，不能只靠 Prompt。

## 20. 错误处理与降级

### 20.1 Realtime Provider 失败

- Session 建立前失败：返回明确错误码，设备保持可重试状态。
- 会话中断：关闭当前 Provider session，不复用失效上下文。
- AUTO 模式可以按显式规则切到 Cascade；是否降级以及原因写入 Trace。
- 不允许悄悄切模型而不记录实际执行模型。

### 20.2 ASR / TTS 失败

Cascade 模式每一阶段独立记录错误。

- ASR 失败：不调用 ChatModel。
- ChatModel 失败：不生成伪造回答。
- TTS 失败：可将文本结果返回设备，由设备根据能力决定是否本地播报；该行为必须由协议能力协商决定。

### 20.3 网络断开

- 服务端释放 Provider session 和运行时资源。
- Conversation 标记异常结束。
- 已确认完成的 message 保留；未完成增量不伪装成完整回答。

## 21. 多租户与安全

所有 AI 核心表都必须租户隔离。

服务端原则：

- 从认证上下文确定租户和 Robot，不信任请求体中的租户字段。
- Agent、Prompt、Model、Memory、Conversation 查询均追加 `tenant_id`。
- Member Memory 读取和写入同时校验 Member-Robot 绑定。
- Realtime Session 不能跨租户恢复。
- Provider API Key 仅服务端可用。
- Tool/Skill 参数需要 Schema 校验。
- 高风险工具调用继续沿用平台确认策略。

## 22. 管理后台

V1 AI 中心只实现六个主页面：

1. 智能体：Agent CRUD、模型/Prompt/记忆策略配置、Robot 绑定。
2. Prompt：System Prompt 和内部 Prompt 模板、版本管理。
3. 模型：Qwen / DeepSeek / Doubao Provider 与 Model 配置。
4. 对话记录：查看 USER / ASSISTANT / TOOL_CALL / TOOL_RESULT 和耗时。
5. 长期记忆：按 Member / Robot 查看、修改、失效记忆。
6. 实时会话：Session 状态、模式、实际模型、延迟、错误、打断次数。

机器人详情页增加“智能体”页签，展示默认 Agent 和已绑定 Agent。

V1 不在后台暴露 Provider 原始协议字段；供应商特有配置放入受控高级配置区。

## 23. V1 核心数据库范围

V1 首批核心表控制为：

```text
ai_model_provider
ai_model
ai_prompt
ai_agent
ai_agent_robot
ai_conversation
ai_conversation_message
ai_realtime_session
ai_memory
```

共 9 张核心表。

Skill / Tool 继续沿用 AI 总体规划并实现必要接口，但本实时对话子项目不以扩大表数量为目标。

## 24. 实施顺序

依赖顺序：

```text
1. AI Module 基础与数据库迁移
2. Model Provider / Model Registry
3. Prompt + Agent
4. Agent <-> Robot
5. Conversation / Trace
6. Realtime Gateway + 平台协议
7. Qwen Native Realtime
8. Doubao Native Realtime
9. Cascade: ASR -> DeepSeek -> TTS
10. Interrupt / Cancellation
11. Memory Pipeline
12. 管理后台
13. 端到端测试与观测指标
```

先用一个 Native Provider 跑通平台协议，再增加第二个 Provider，避免同时调试设备协议和多个供应商协议。

## 25. 测试策略

### 单元测试

- ModelClientRegistry 按模型能力解析正确 Adapter。
- Agent 与 Robot 跨租户绑定被拒绝。
- Memory scope 约束和成员授权。
- Memory Policy 的记住/忽略/忘记规则。
- AUTO 模式路由规则。
- Tool 风险策略。

### 集成测试

- WebSocket 鉴权和 Session 生命周期。
- Binary Audio + Text Control Frame 顺序。
- Provider Mock 下的增量文本和音频回传。
- 打断取消旧 turn 后不会继续向设备输出旧音频。
- 网络断开能释放 Provider session。
- Conversation/Message/RealtimeSession Trace 完整。

### 端到端测试

至少覆盖：

1. RK3588 -> Qwen Native -> Audio Response。
2. RK3588 -> Doubao Native -> Audio Response。
3. RK3588 -> ASR -> DeepSeek -> TTS -> Audio Response。
4. 播报过程中用户打断。
5. 声纹身份成功后写入 MEMBER Memory。
6. 匿名会话不会写入 MEMBER Memory。
7. 更换 Agent 后 Member Memory 仍可按权限复用。
8. Agent 触发机器人 Skill 时只能经 Mission/Tool 边界。

## 26. 验收标准

功能验收：

- 管理员可配置 Qwen、DeepSeek、Doubao Provider 和 Model。
- 可创建 Agent、配置 Prompt、Realtime/Cascade 模型和记忆策略。
- Robot 可绑定多个 Agent 并设置一个默认 Agent。
- RK3588 使用同一平台协议完成 Native 和 Cascade 两类会话。
- 支持流式文本、流式音频和用户打断。
- 能按身份安全读取/写入长期记忆。
- 历史会话能追踪实际使用模型、Prompt、Tool/Skill、耗时和错误。

安全验收：

- 跨租户 Agent、Model、Memory、Conversation 访问被拒绝。
- 匿名或低置信身份不写成员长期记忆。
- Provider Key 不出现在 API 返回、前端状态和应用日志中。
- LLM 无法绕过 Tool/Skill/Mission 直接控制 MQTT、ROS 或数据库。

架构验收：

- RK3588 无供应商协议代码。
- 上层 Agent Runtime 无散落的 Provider 条件分支。
- 实时音频不经过 MQTT。
- 记忆异步提炼不阻塞实时回答。
- 所有关键行为可通过 Trace 解释。

## 27. 后续扩展边界

完成 V1 后可以在不改变设备协议和 Agent 主模型的前提下扩展：

- 更多 Realtime/Chat/ASR/TTS Provider。
- APP/H5 语音客户端。
- 知识库 RAG。
- Embedding/向量记忆召回。
- 数字人输出协议。
- Agent Tool Marketplace。
- Prompt A/B 测试。
- 记忆敏感分类和 Agent 读取策略。
- 多 Agent 协作。

这些能力不属于本 V1 的实施前置条件。
