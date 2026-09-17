# Realtime Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide one tenant-safe WebSocket protocol for RK3588 and route it to Qwen/Doubao native realtime voice or an ASR→DeepSeek→TTS cascade, with streaming output, cancellation and trace persistence.

**Architecture:** The inbound device protocol is vendor-neutral and uses JSON control frames plus binary PCM frames. A `RealtimeAgentRuntime` owns each logical session; provider adapters implement `RealtimeVoiceClient`, while cascade components implement `AsrClient`, `ChatModelClient` and `TtsClient`. Existing `DeviceSession` identity supplies trusted `tenantId` and `robotId` during WebSocket handshake.

**Tech Stack:** Java 17, Spring Boot WebSocket, JDK `java.net.http.HttpClient`/`WebSocket`, Jackson, MyBatis Plus, MySQL, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-17-realtime-agent-design.md`

## Global Constraints

- Requires Plan 1 core Agent/model/prompt/binding work.
- RK3588 never receives provider API keys and never speaks provider-specific protocols.
- Device identity comes from the authenticated `DeviceSession`; request payload cannot override tenant/robot identity.
- Realtime audio does not use MQTT.
- Native S2S: Qwen and Doubao. Cascade chat: DeepSeek; ASR/TTS remain configurable model adapters.
- Cancellation must stop old-turn output; stale audio must never leak into the next turn.
- Store transcripts/trace by default, not raw household audio.

## Current provider contracts to implement against

- Qwen Omni Realtime: WebSocket, Bearer auth, model query parameter, JSON event protocol; `qwen3.5-omni-plus-realtime` supports realtime text/audio and function calling. Official reference: `https://help.aliyun.com/en/model-studio/realtime`.
- DeepSeek Chat Completions: `POST /chat/completions`, `stream=true` returns data-only SSE and terminates with `data: [DONE]`. Official reference: `https://api-docs.deepseek.com/api/create-chat-completion/`.
- Doubao end-to-end realtime voice: implement behind its own codec/transport so binary framing and resource headers do not leak into runtime. Official product/API references are under `https://www.volcengine.com/docs/6561/1594360` and the linked API reference for the enabled S2S resource.

---

### Task 1: Add conversation and realtime-session persistence

**Files:**
- Modify: `sql/mysql/robot-platform.sql`
- Create: `.../ai/conversation/dal/dataobject/AiConversationDO.java`
- Create: `.../ai/conversation/dal/dataobject/AiConversationMessageDO.java`
- Create: `.../ai/realtime/dal/dataobject/AiRealtimeSessionDO.java`
- Create corresponding mappers and services.
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/conversation/AiConversationServiceTest.java`

**Interfaces:**
- `long startConversation(long tenantId, long agentId, long robotId, Long memberId, String channel)`
- `void appendMessage(AiConversationMessage message)`
- `long startRealtimeSession(RealtimeSessionStart start)`
- `void finishRealtimeSession(long tenantId, long id, RealtimeSessionFinish finish)`

- [ ] **Step 1: Write failing persistence/service tests**

Verify tenant-qualified retrieval, message ordering by `(conversationId, createdAt, id)`, and abnormal session termination persists `errorCode`.

- [ ] **Step 2: Add tables**

Add exactly:

```text
ai_conversation
ai_conversation_message
ai_realtime_session
```

Indexes must support:

```sql
KEY idx_ai_conversation_robot (`tenant_id`,`robot_id`,`started_at`),
KEY idx_ai_message_conversation (`tenant_id`,`conversation_id`,`created_at`,`id`),
KEY idx_ai_realtime_robot (`tenant_id`,`robot_id`,`connected_at`)
```

- [ ] **Step 3: Implement DOs, mappers and services**

Use a runtime value object:

```java
public record RealtimeSessionStart(long tenantId, long conversationId, long agentId,
        long robotId, Long memberId, String mode, Long providerId, Long modelId,
        String providerSessionId) { }
```

- [ ] **Step 4: Run tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sql/mysql/robot-platform.sql robot-platform-module-ai
git commit -m "feat(ai): persist realtime conversations"
```

---

### Task 2: Define the platform Realtime Agent Protocol codec

**Files:**
- Create: `.../ai/realtime/protocol/RealtimeClientEvent.java`
- Create: `.../ai/realtime/protocol/RealtimeServerEvent.java`
- Create: `.../ai/realtime/protocol/RealtimeProtocolCodec.java`
- Create: `.../ai/realtime/protocol/RealtimeAudioFormat.java`
- Test: `.../ai/realtime/protocol/RealtimeProtocolCodecTest.java`

**Interfaces:**
- Text frame decode: `RealtimeClientEvent decodeClientText(String json)`
- Text frame encode: `String encodeServerEvent(RealtimeServerEvent event)`
- Binary frames are passed as `ByteBuffer` without JSON/Base64 conversion at the platform boundary.

- [ ] **Step 1: Write protocol tests using fixed JSON fixtures**

Required client event types:

```text
session.start
input.speech_started
input.speech_stopped
session.close
```

Required server event types:

```text
session.created
session.error
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

Assert every server event carries `sessionId`, `turnId` when turn-scoped, monotonically assigned `sequence`, and `serverTime`.

- [ ] **Step 2: Implement sealed event types**

Example:

```java
public sealed interface RealtimeClientEvent permits SessionStartEvent, SpeechStartedEvent,
        SpeechStoppedEvent, SessionCloseEvent {
    String type();
}
```

`SessionStartEvent` contains only `agentCode`, candidate `identity`, and audio format. It does not contain authoritative tenant/robot IDs.

- [ ] **Step 3: Run tests**

Run: `mvn -pl robot-platform-module-ai -Dtest=RealtimeProtocolCodecTest test`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): define realtime device protocol"
```

---

### Task 3: Add authenticated `/device-api/ai/realtime` WebSocket gateway

**Files:**
- Modify: `robot-platform-module-ai/pom.xml` to add `spring-boot-starter-websocket`.
- Create: `.../ai/realtime/gateway/AiRealtimeWebSocketConfiguration.java`
- Create: `.../ai/realtime/gateway/AiRealtimeHandshakeInterceptor.java`
- Create: `.../ai/realtime/gateway/AiRealtimeWebSocketHandler.java`
- Test: `.../ai/realtime/gateway/AiRealtimeHandshakeInterceptorTest.java`
- Test: `.../ai/realtime/gateway/AiRealtimeWebSocketHandlerTest.java`

**Interfaces:**
- Endpoint: `/device-api/ai/realtime`
- Handshake attribute: trusted `DeviceSession` from the existing device auth/session subsystem.
- Handler delegates all business state to `RealtimeAgentRuntimeManager`.

- [ ] **Step 1: Write handshake security tests**

Assert:

1. missing/invalid device token rejects upgrade;
2. authenticated session exposes exact `tenantId`, `deviceId`, `robotId` from `DeviceSession`;
3. a body/query parameter cannot replace these values.

- [ ] **Step 2: Implement handshake integration with existing device session token service**

Store only the resolved `DeviceSession` in WebSocket attributes. Do not store the opaque token after authentication.

- [ ] **Step 3: Write handler tests**

Verify text frames go through `RealtimeProtocolCodec`, binary frames call `runtime.acceptAudio(ByteBuffer)`, and connection close calls `runtime.close(reason)` exactly once.

- [ ] **Step 4: Implement gateway**

Keep handler thin:

```java
@Override
protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
    runtimeManager.require(ws.getId()).acceptAudio(message.getPayload());
}
```

- [ ] **Step 5: Run tests and commit**

Run: `mvn -pl robot-platform-module-ai -am test`

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): add authenticated realtime websocket gateway"
```

---

### Task 4: Implement runtime session state and deterministic model routing

**Files:**
- Create: `.../ai/realtime/runtime/RealtimeAgentRuntime.java`
- Create: `.../ai/realtime/runtime/RealtimeAgentRuntimeManager.java`
- Create: `.../ai/realtime/runtime/RealtimeRoute.java`
- Create: `.../ai/realtime/runtime/RealtimeModelRouter.java`
- Test: `.../ai/realtime/runtime/RealtimeModelRouterTest.java`
- Test: `.../ai/realtime/runtime/RealtimeAgentRuntimeTest.java`

**Interfaces:**
- `RealtimeRoute route(AiAgentConfig agent, ModelCapabilities capabilities)`
- `void acceptControl(RealtimeClientEvent event)`
- `void acceptAudio(ByteBuffer pcm)`
- `void close(CloseReason reason)`

- [ ] **Step 1: Write routing tests**

Rules are deterministic:

```text
NATIVE  -> require native S2S route
CASCADE -> require ASR + Chat + TTS route
AUTO    -> native for ordinary realtime conversation; cascade when the turn explicitly requires configured chat-only capability/tool path or native capability is unsupported
```

Record `routeReason` such as `AGENT_NATIVE`, `AGENT_CASCADE`, `AUTO_NATIVE`, `AUTO_CAPABILITY_FALLBACK`.

- [ ] **Step 2: Implement runtime state machine**

States:

```text
CONNECTING -> READY -> USER_SPEAKING -> ASSISTANT_RESPONDING -> READY -> CLOSED
```

Reject invalid transitions instead of silently accepting frames.

- [ ] **Step 3: Resolve `session.start` safely**

Use trusted `DeviceSession.tenantId()/robotId()`, then call:

```java
bindingService.requireAgentForRobot(tenantId, robotId, agentCode);
agentService.getResolvedConfig(tenantId, agentId);
```

Candidate member identity is validated in Plan 3; until then runtime stores it as untrusted metadata and uses `memberId=null` for long-term behavior.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl robot-platform-module-ai -am test`

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): add realtime agent runtime"
```

---

### Task 5: Define provider client contracts and registry

**Files:**
- Create: `.../ai/model/client/RealtimeVoiceClient.java`
- Create: `.../ai/model/client/ChatModelClient.java`
- Create: `.../ai/model/client/AsrClient.java`
- Create: `.../ai/model/client/TtsClient.java`
- Create: `.../ai/model/client/ModelClientRegistry.java`
- Create common callback/event records under `.../ai/model/client/event/`.
- Test: `.../ai/model/client/ModelClientRegistryTest.java`

**Interfaces:**

```java
interface RealtimeVoiceClient {
    RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener);
}
interface RealtimeProviderSession extends AutoCloseable {
    void appendAudio(ByteBuffer pcm);
    void speechStarted();
    void speechStopped();
    void cancelCurrentResponse();
}
interface ChatModelClient {
    ChatStream stream(ChatRequest request, ChatListener listener);
}
```

- [ ] **Step 1: Test registry selection by `(providerType, modelType)`**

No Agent/runtime class may switch directly on provider string.

- [ ] **Step 2: Implement registry and event normalization**

Normalize provider output to:

```text
TranscriptDelta
TranscriptDone
TextDelta
TextDone
AudioDelta
AudioDone
ToolCall
Usage
ProviderError
```

- [ ] **Step 3: Run tests and commit**

```bash
mvn -pl robot-platform-module-ai -am test
git add robot-platform-module-ai
git commit -m "feat(ai): add model client registry"
```

---

### Task 6: Implement Qwen native realtime adapter first

**Files:**
- Create: `.../ai/model/realtime/qwen/QwenRealtimeVoiceClient.java`
- Create: `.../ai/model/realtime/qwen/QwenRealtimeSession.java`
- Create: `.../ai/model/realtime/qwen/QwenRealtimeCodec.java`
- Test: `.../ai/model/realtime/qwen/QwenRealtimeCodecTest.java`
- Test: `.../ai/model/realtime/qwen/QwenRealtimeVoiceClientTest.java`

**Interfaces:**
- Consumes decrypted provider credential only inside adapter creation.
- Produces normalized provider events from Task 5.

- [ ] **Step 1: Capture Qwen JSON protocol fixtures in tests**

Fixture coverage must include `session.update`, audio append, transcript delta/done, response text/audio delta, completion, error and cancellation events. The platform device protocol remains binary; Base64 exists only inside the Qwen adapter where required by Qwen's JSON protocol.

- [ ] **Step 2: Test connection construction**

Assert URI is `provider.baseUrl + ?model=<modelCode>` and header is:

```text
Authorization: Bearer <decrypted-key>
```

The model code comes from `ai_model.model_code`, not source constants.

- [ ] **Step 3: Implement with JDK 17 `HttpClient.newWebSocketBuilder()`**

On open send a `session.update` built from Agent prompt and `voice_config_json`. Map provider audio output to `AudioDelta(ByteBuffer)`.

- [ ] **Step 4: Connect Qwen adapter to runtime**

First working vertical slice:

```text
RK3588 protocol fixture -> Runtime -> Qwen adapter mock -> text/audio server events
```

- [ ] **Step 5: Run tests and commit**

```bash
mvn -pl robot-platform-module-ai -am test
git add robot-platform-module-ai
git commit -m "feat(ai): support qwen realtime voice"
```

---

### Task 7: Implement Doubao native realtime adapter as an isolated transport/codec

**Files:**
- Create: `.../ai/model/realtime/doubao/DoubaoRealtimeVoiceClient.java`
- Create: `.../ai/model/realtime/doubao/DoubaoRealtimeSession.java`
- Create: `.../ai/model/realtime/doubao/DoubaoRealtimeCodec.java`
- Test resources: `robot-platform-module-ai/src/test/resources/ai/doubao/`
- Test: `.../ai/model/realtime/doubao/DoubaoRealtimeCodecTest.java`

**Interfaces:**
- Same `RealtimeVoiceClient` contract as Qwen.
- Provider-specific `appId`, `resourceId`, protocol flags and voice/persona fields are read from `ai_model_provider.config_json` / `ai_model.config_json`; access token remains encrypted provider secret.

- [ ] **Step 1: Add protocol fixture tests before transport code**

Store sanitized request/response examples from the currently enabled Volcengine S2S API in `src/test/resources/ai/doubao/`. Tests assert exact binary/header/event decoding into normalized events.

- [ ] **Step 2: Implement `DoubaoRealtimeCodec` with no runtime dependencies**

Codec owns all provider framing details. Runtime and Agent layers must never import Doubao frame/header classes.

- [ ] **Step 3: Implement transport with configurable base URL/resource ID**

Build authentication/resource headers from provider configuration and decrypted secret. Never hardcode account-specific app IDs, resource IDs or tokens.

- [ ] **Step 4: Run adapter contract tests**

Reuse a common abstract contract test so Qwen and Doubao both prove:

```text
open -> append audio -> receive transcript/text/audio -> cancel -> close
```

- [ ] **Step 5: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): support doubao realtime voice"
```

---

### Task 8: Implement DeepSeek streaming chat and the Cascade pipeline

**Files:**
- Create: `.../ai/model/chat/deepseek/DeepSeekChatModelClient.java`
- Create: `.../ai/model/chat/deepseek/DeepSeekSseDecoder.java`
- Create: `.../ai/realtime/runtime/CascadeRealtimePipeline.java`
- Create initial configurable `AsrClient` and `TtsClient` adapters for the chosen Qwen models under `.../ai/model/asr/qwen/` and `.../ai/model/tts/qwen/`.
- Test: `.../ai/model/chat/deepseek/DeepSeekSseDecoderTest.java`
- Test: `.../ai/realtime/runtime/CascadeRealtimePipelineTest.java`

**Interfaces:**
- DeepSeek request: `POST {baseUrl}/chat/completions`, Bearer auth, `stream=true`.
- SSE decoder emits `TextDelta`, tool call deltas, usage and completion.
- Cascade: streaming ASR final transcript -> Chat stream -> sentence/chunk buffer -> streaming TTS -> platform audio.

- [ ] **Step 1: Test DeepSeek SSE decoder**

Include a fixture containing normal content chunks, a tool call chunk, final usage chunk and `data: [DONE]`.

- [ ] **Step 2: Implement DeepSeek client with JDK `HttpClient`**

Do not use an OpenAI SDK dependency merely for this API. Preserve cancelability by keeping the active request/future handle in `ChatStream`.

- [ ] **Step 3: Test cascade ordering**

Given final ASR text `你好`, assert Chat starts only after ASR finalization; TTS starts after the first speakable text chunk and preserves text order.

- [ ] **Step 4: Implement cascade pipeline**

Use bounded buffers; on cancellation, cancel ASR/Chat/TTS handles and discard queued audio for the old turn.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -pl robot-platform-module-ai -am test
git add robot-platform-module-ai
git commit -m "feat(ai): add deepseek cascade voice pipeline"
```

---

### Task 9: Implement interruption and stale-output protection

**Files:**
- Modify: `.../ai/realtime/runtime/RealtimeAgentRuntime.java`
- Create: `.../ai/realtime/runtime/TurnGeneration.java`
- Test: `.../ai/realtime/runtime/RealtimeInterruptionTest.java`

**Interfaces:**
- Each response owns immutable `turnId` plus a monotonically increasing generation number.
- Provider callback is accepted only if it matches the active generation.

- [ ] **Step 1: Write race-condition test**

Scenario:

```text
turn A audio starts -> user speech_started -> cancel A -> turn B begins -> late A audio callback arrives
```

Expected: late A callback is dropped; `playback.stop` and `assistant.interrupted` are emitted once; B output continues normally.

- [ ] **Step 2: Implement generation guard**

```java
if (!activeGeneration.matches(callback.turnId(), callback.generation())) return;
```

Cancellation order:

```text
mark generation cancelled -> emit playback.stop -> cancel provider/pipeline -> discard queued old audio -> accept new input
```

- [ ] **Step 3: Run repeated concurrency tests**

Run the interruption test repeatedly (for example 100 iterations) and verify no stale-audio assertion fails.

- [ ] **Step 4: Commit**

```bash
git add robot-platform-module-ai
git commit -m "fix(ai): make realtime interruption generation safe"
```

## Plan 2 Exit Criteria

- `/device-api/ai/realtime` authenticates using existing `DeviceSession` identity.
- Platform protocol uses JSON control + binary audio and is independent of vendors.
- Qwen native realtime passes adapter and gateway tests.
- Doubao native realtime passes the same normalized adapter contract.
- DeepSeek works through the cascade path with configurable ASR/TTS.
- Native/Cascade/AUTO decisions are deterministic and traced.
- Interrupting playback prevents any stale old-turn audio from reaching RK3588.
- Conversation and realtime session records contain model/latency/error data without raw audio or secrets.
