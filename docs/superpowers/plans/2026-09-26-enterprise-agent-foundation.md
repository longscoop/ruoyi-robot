# Enterprise Agent Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make immutable enterprise agent versions run through both robot voice and admin web text, with test/production publication, rollback, tenant isolation, and basic execution traces.

**Architecture:** Extend `robot-platform-module-ai` with a versioned configuration and release pointer. A shared resolver supplies the same published snapshot to the existing realtime runtime and a new web text stream; each run records its version and core events in MySQL.

**Tech Stack:** Java 17, Spring Boot, MyBatis Plus, MySQL 8.4, Vue 3, TypeScript, Vitest, Maven.

**Spec:** `docs/superpowers/specs/2026-09-26-enterprise-agent-design.md` §§1–5, 10, 12 (first delivery segment). Subsequent plans will cover knowledge/tools/workflow and then memory/quotas/alerts/evaluation.

## Global Constraints

- Keep all code in the existing `robot-platform-module-ai`; do not introduce a separate orchestration service.
- Robot control continues through Robot/Mission; do not expose direct MQTT, ROS, SQL, or Shell tools.
- Every lookup and mutation must enforce the current `tenant_id`; never trust tenant or robot ownership from a request body.
- Keep Provider API keys server-side and encrypted; never return them in API responses, SSE, traces, or logs.
- Existing robot realtime sessions keep the version resolved at session start.
- A published version is immutable; drafts and new releases affect only subsequent sessions.
- Add both fresh-database schema and a runnable upgrade path for existing MySQL volumes.

## Review Focus

- Concurrent releases of the same agent: only one compare-and-swap succeeds; test in Task 3.
- Missing or disabled published model: run fails before opening a provider stream and records a safe error; test in Task 5.
- Cross-tenant version ID submitted to any route: reject without disclosing its contents; test in Tasks 3 and 6.
- Disconnected web stream: cancel provider stream and close run with a terminal state; test in Task 6.
- Existing robot session during rollback: keep its original snapshot; test in Task 7.

## File Map

- `sql/mysql/robot-platform.sql`, `sql/mysql/upgrade/2026-09-26-enterprise-agent-foundation.sql`: fresh and existing database schema for draft fields, versions, releases, runs and events.
- `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/version/`: immutable snapshot, release persistence, service, and admin API.
- `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/route/`: validated model parameters and fallback selection.
- `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/`: trusted run identity, published-version resolver, run/event recording, and web text execution.
- `robot-platform-module-ai/src/main/java/com/robot/platform/ai/realtime/runtime/RealtimeAgentRuntime.java`: resolve production snapshot at session start while retaining realtime transport and interruption behavior.
- `robot-platform-ui-admin/src/api/ai/enterprise/` and `src/views/ai/enterprise/`: API clients and working agent/model, release, trace, and web conversation pages.

---

### Task 1: Persist enterprise drafts, versions, releases and runs

**Files:**
- Modify: `sql/mysql/robot-platform.sql`
- Create: `sql/mysql/upgrade/2026-09-26-enterprise-agent-foundation.sql`
- Create: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/schema/EnterpriseAgentSchemaContractTest.java`

**Interfaces:**
- Produces tables `ai_agent_version`, `ai_agent_release`, `ai_agent_release_log`, `ai_run`, `ai_run_event`; adds `responsibility`, `input_requirements_json`, `output_requirements_json`, `model_route_json` to `ai_agent`. Makes `ai_conversation.robot_id` nullable and adds `actor_type`, `actor_id`, `agent_version_id` for web ownership and version pinning.
- Version key `(tenant_id, agent_id, version_no)`; release key `(tenant_id, agent_id, environment)`; run key `id` with mandatory tenant, agent, version and channel.

- [ ] **Step 1: Write failing schema tests** asserting all tables/keys/tenant fields exist in both SQL files, the upgrade script is safe to rerun, and `api_key_ciphertext` is absent from run/event columns.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=EnterpriseAgentSchemaContractTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect the new test to fail.
- [ ] **Step 3: Add MySQL 8.4 DDL.** The upgrade script checks `information_schema` before each added column and uses `CREATE TABLE IF NOT EXISTS` for new tables. Add a script-level check for repeated execution against an existing volume.
- [ ] **Step 4: Run the focused test and execute the upgrade script twice against a disposable MySQL 8.4 database**; both executions must succeed and preserve existing agent rows.
- [ ] **Step 5: Commit** schema and test as `feat(ai): add enterprise agent persistence`.

### Task 2: Validate agent draft contracts and model route settings

**Files:**
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/dal/dataobject/AiAgentDO.java`
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/service/AiAgentService.java`, `AiAgentServiceImpl.java`
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/controller/admin/AiAgentAdminController.java`
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/controller/admin/AiModelAdminController.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/route/ModelRoutePolicy.java`, `ModelRoutePolicyValidator.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/agent/AiAgentServiceTest.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/model/route/ModelRoutePolicyValidatorTest.java`

**Interfaces:**
- `ModelRoutePolicyValidator.validate(long tenantId, String modelRouteJson): ModelRoutePolicy` validates `{"chat":{"primaryModelId":201,"fallbackModelIds":[202],"timeoutMs":30000,"maxRetries":1,"temperature":0.7,"maxOutputTokens":1024}}`: CHAT models in the same tenant, timeout 1000–120000 ms, retries 0–2, temperature 0–2, output tokens 1–8192, and no fallback cycle.
- Input requirements JSON is `{"maxInputChars":20000}`; output requirements JSON is `{"format":"TEXT"}` or `{"format":"JSON","jsonSchema":{...}}`. Omitted values use `20000` and `TEXT`; reject unknown keys and malformed JSON.
- Agent create/update commands gain responsibility, input/output requirements JSON, and route JSON; existing clients may omit them and retain current behavior.

- [ ] **Step 1: Write failing tests** for same-tenant route acceptance, cross-tenant fallback rejection, cyclic fallback rejection, malformed contract JSON, and existing client compatibility.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=AiAgentServiceTest,ModelRoutePolicyValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect the new assertions to fail.
- [ ] **Step 3: Implement** draft fields and `validate(...)`; reject unknown input/output contract shapes and keep existing route modes `NATIVE/CASCADE/AUTO` valid.
- [ ] **Step 4: Run the focused tests and `AiAdminControllerTest`**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): validate enterprise agent drafts`.

### Task 3: Create immutable versions and transactional releases

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/version/dal/dataobject/AiAgentVersionDO.java`, `AiAgentReleaseDO.java`, `AiAgentReleaseLogDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/version/dal/mysql/AiAgentVersionMapper.java`, `AiAgentReleaseMapper.java`, `AiAgentReleaseLogMapper.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/version/AgentSnapshot.java`, `AgentPublicationService.java`, `AgentPublicationServiceImpl.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/version/controller/admin/AiAgentPublicationAdminController.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/agent/version/AgentPublicationServiceTest.java`

**Interfaces:**
- `AgentPublicationService.createVersion(long tenantId, long agentId): AgentSnapshot` copies validated draft and exact Prompt content/version; knowledge/tool/workflow lists are empty until the second delivery segment.
- `publish(long tenantId, long agentId, long versionId, String environment, Long expectedReleaseRevision, long operatorId): AgentSnapshot` performs optimistic compare-and-swap. `rollback(long tenantId, long agentId, long targetVersionId, String environment, Long expectedReleaseRevision, long operatorId): AgentSnapshot` uses the same update path and logs the previous version.
- `requirePublished(long tenantId, long agentId, String environment): AgentSnapshot` never consults mutable draft fields.
- Admin routes: `POST/GET /admin-api/ai/agents/{id}/versions`, `POST/GET /admin-api/ai/agents/{id}/releases`, `POST /admin-api/ai/agents/{id}/rollback` with create/query/publish permissions.

- [ ] **Step 1: Write failing service tests** for immutable Prompt/config capture, cross-tenant version rejection, disabled referenced model rejection, TEST/PRODUCTION separation, rollback history, and concurrent release conflict.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=AgentPublicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect failure.
- [ ] **Step 3: Implement** mappers, service transactions, and controller; use a conditional SQL update with expected release revision and never update a version row after insertion.
- [ ] **Step 4: Run focused service and controller tests**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): publish immutable agent versions`.

### Task 4: Add trusted run identity and basic trace persistence

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/RunContext.java`, `PublishedAgentResolver.java`, `AgentRunService.java`, `AgentRunServiceImpl.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/dal/dataobject/AiRunDO.java`, `AiRunEventDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/dal/mysql/AiRunMapper.java`, `AiRunEventMapper.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/controller/admin/AiRunAdminController.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/run/AgentRunServiceTest.java`

**Interfaces:**
- `PublishedAgentResolver.resolve(long tenantId, long agentId, String environment): AgentSnapshot` delegates to Task 3 and validates enabled referenced models.
- `AgentRunService.start(RunContext context): long`, `event(RunContext context, long runId, String type, String status, String safeJson): void`, `finish(RunContext context, long runId, String status, String errorCode): void`; `RunContext` contains server-derived tenant, actor, channel, environment, version, conversation, robot and request ID. Every method checks the run belongs to the context tenant.
- `GET /admin-api/ai/runs` and `GET /admin-api/ai/runs/{id}/events` return tenant-scoped paged traces under `ai:run:query`.

- [ ] **Step 1: Write failing tests** for tenant mismatch, version pinning, ordered events, idempotent finish, and omission of provider credentials from trace responses.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=AgentRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect failure.
- [ ] **Step 3: Implement** the run service, mapper queries, resolver and trace API; store only allowlisted event fields.
- [ ] **Step 4: Run focused tests**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): trace published agent runs`.

### Task 5: Route chat models with bounded fallback

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/route/ChatModelRouteService.java`, `ChatModelRouteServiceImpl.java`
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/client/ChatRequest.java` to carry validated timeout, temperature and max output tokens from the route policy while retaining its existing constructors.
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/model/route/ChatModelRouteServiceTest.java`

**Interfaces:**
- `ChatModelRouteService.stream(RunContext context, long runId, AgentSnapshot snapshot, List<ChatRequest.ChatMessage> messages, ChatListener listener): ChatStream` uses the snapshot's CHAT route and Task 4 trace context.
- Fallback occurs only before user-visible output and only for configured retriable failures; if the model/provider is disabled, fail before opening a stream and record a safe error.

- [ ] **Step 1: Write failing tests** for selected model, configured timeout/parameters, pre-output fallback, no fallback after output, disabled model, and all routes failing.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=ChatModelRouteServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect failure.
- [ ] **Step 3: Implement** route selection using `ResolvedModelResolver` and `ModelClientRegistry`; cap retries and close losing streams.
- [ ] **Step 4: Run focused tests and existing model client tests**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): add bounded chat model routing`.

### Task 6: Stream a production web conversation

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/run/web/WebChatRunService.java`, `WebChatRunServiceImpl.java`, `AiWebChatController.java`
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/conversation/service/AiConversationService.java`, `AiConversationServiceImpl.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/run/web/WebChatRunServiceTest.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/run/web/AiWebChatControllerTest.java`

**Interfaces:**
- `WebChatRunService.stream(long agentId, Long conversationId, String text, long loginUserId, boolean testEnvironment): SseEmitter`; tenant comes from `TenantContextHolder`, never the request.
- `POST /admin-api/ai/web-chat/stream` uses `ai:agent:use`; `GET /admin-api/ai/web-chat/conversations` and `GET .../{id}/messages` enforce actor ownership.
- Events are `run.started`, `response.delta`, `response.done`, and `run.failed`; all carry run ID and no secrets.

- [ ] **Step 1: Write failing tests** for production and test release selection, actor ownership, cross-tenant conversation denial, disconnected SSE cancellation, and error finalization.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=WebChatRunServiceTest,AiWebChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect failure.
- [ ] **Step 3: Implement** controller, executor and conversation methods using Tasks 3–5; extend `startConversation` to accept nullable `Long robotId` plus actor/version fields, and persist user and assistant messages under the pinned version/run.
- [ ] **Step 4: Run focused tests**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): stream enterprise web chat`.

### Task 7: Pin robot voice sessions to the production release

**Files:**
- Modify: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/realtime/runtime/RealtimeAgentRuntime.java`, `RealtimeAgentRuntimeManager.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/realtime/runtime/RealtimeAgentRuntimeTest.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/realtime/runtime/QwenRealtimeRuntimeSliceTest.java`

**Interfaces:**
- At authenticated session start, resolve `PRODUCTION` through Task 4, adapt `AgentSnapshot` to existing `AiAgentConfig`, and create a Task 4 run. Keep the snapshot in the session object until close.
- Record start, model selection, completion, interruption and terminal error events without blocking audio frames on database writes.

- [ ] **Step 1: Write failing tests** for no production release, same-tenant robot binding, rollback during active session retaining the original snapshot, and interruption trace correctness.
- [ ] **Step 2: Run** `mvn -pl robot-platform-module-ai -am -Dtest=RealtimeAgentRuntimeTest,QwenRealtimeRuntimeSliceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expect failure.
- [ ] **Step 3: Implement** publication resolution and run lifecycle while preserving current WebSocket protocol and interruption generation checks.
- [ ] **Step 4: Run all AI realtime runtime tests**; expect PASS.
- [ ] **Step 5: Commit** as `feat(ai): pin robot voice to published agents`.

### Task 8: Connect administration UI and permissions end to end

**Files:**
- Create: `robot-platform-ui-admin/src/api/ai/enterprise/agent.ts`, `model.ts`, `provider.ts`, `prompt.ts`, `release.ts`, `run.ts`, `webChat.ts`
- Create: `robot-platform-ui-admin/src/views/ai/enterprise/agents/index.vue`, `models/index.vue`, `releases/index.vue`, `runs/index.vue`, `chat/index.vue`
- Create: `robot-platform-ui-admin/src/views/ai/enterprise/enterpriseAgentApi.spec.ts`
- Modify: `sql/mysql/robot-platform.sql`, `sql/mysql/upgrade/2026-09-26-enterprise-agent-foundation.sql`

**Interfaces:**
- UI calls the plural `/ai/agents`, `/ai/models`, `/ai/providers`, `/ai/prompts`, `/ai/runs` admin routes and the web chat route from Task 6. Do not route new menu items to legacy `/ai/model/*` pages.
- Menu permissions include `ai:agent:query/create/update/delete/use/publish`, `ai:model:query/create/update/delete`, `ai:provider:query/create/update/delete`, `ai:prompt:query/create`, `ai:run:query` with server checks.

- [ ] **Step 1: Write failing API mapping tests** that assert client URLs, version/release payloads, and no credential fields in response types.
- [ ] **Step 2: Run** `pnpm --dir robot-platform-ui-admin test -- enterpriseAgentApi.spec.ts`; expect failure.
- [ ] **Step 3: Implement** API clients, pages, forms and menu SQL; show release environment/version, trace timeline and live web text stream.
- [ ] **Step 4: Run** `pnpm --dir robot-platform-ui-admin test -- enterpriseAgentApi.spec.ts`, `pnpm --dir robot-platform-ui-admin ts:check`, `pnpm --dir robot-platform-ui-admin build:local`, and `mvn -pl robot-platform-module-ai -am test`; expect PASS.
- [ ] **Step 5: Run two-channel end-to-end checks** with a test agent: web reply and robot voice session report the same published version; draft edits do not change production; rollback affects only new sessions. Before enabling the new runtime in an existing deployment, use Task 3's admin API to version and publish each enabled legacy agent and verify every bound robot has a production release.
- [ ] **Step 6: Commit** as `feat(ai): expose enterprise agent console`.

## Completion Gate

The first delivery segment is complete only when the upgraded database, admin UI, web text stream and robot voice session all use the same released version, and tenant isolation plus trace checks pass. Then write and execute separate plans for the knowledge/tool/workflow segment and the memory/monitoring/evaluation segment from the approved spec.
