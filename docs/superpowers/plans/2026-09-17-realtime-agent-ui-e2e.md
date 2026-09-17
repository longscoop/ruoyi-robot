# Realtime Agent Admin UI and E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the AI administration pages, robot Agent bindings UI, realtime observability views, and end-to-end verification for the complete realtime Agent feature.

**Architecture:** The Vue 3 admin app uses existing request/auth/menu conventions and talks only to `/admin-api/ai/**`; realtime device traffic remains on `/device-api/ai/realtime`. Backend E2E tests verify tenant isolation and runtime traces; provider-live tests are opt-in and require environment credentials.

**Tech Stack:** Vue 3, TypeScript, Element Plus, existing request utilities, Vitest, Java 17, Spring Boot, JUnit 5, Testcontainers/MySQL.

**Spec:** `docs/superpowers/specs/2026-09-17-realtime-agent-design.md`

## Global Constraints

- Requires Plans 1–3.
- Do not expose provider plaintext/ciphertext secrets to browser state.
- Keep six AI Center pages only: Agent, Prompt, Model, Conversation, Memory, Realtime Session.
- Robot detail gains an Agent tab; do not create a second robot-management flow.
- All views operate in current tenant context.
- Do not add fake dashboard metrics or placeholder data.

---

### Task 1: Add typed AI API clients

**Files:**
- Create: `robot-platform-ui-admin/src/api/ai/model/index.ts`
- Create: `robot-platform-ui-admin/src/api/ai/prompt/index.ts`
- Create: `robot-platform-ui-admin/src/api/ai/agent/index.ts`
- Create: `robot-platform-ui-admin/src/api/ai/conversation/index.ts`
- Create: `robot-platform-ui-admin/src/api/ai/memory/index.ts`
- Create: `robot-platform-ui-admin/src/api/ai/realtime/index.ts`
- Test: colocated `*.test.ts` or repository-standard frontend test paths.

**Interfaces:**
- Models mirror backend admin DTOs.
- Provider DTO contains `apiKeyConfigured: boolean`; no API key value field on response type.

- [ ] **Step 1: Write API contract/type tests**

Ensure provider response cannot be assigned a plaintext key field and endpoints use `/ai/...` paths through the existing admin request prefix.

- [ ] **Step 2: Implement clients**

Expose only page/list/get/create/update/delete functions actually required by V1 pages.

- [ ] **Step 3: Run frontend type check/tests**

Run repository-standard commands, typically:

```bash
cd robot-platform-ui-admin
pnpm type-check
pnpm test --run
```

Expected: PASS in an environment satisfying the repository's documented Node/pnpm requirements.

- [ ] **Step 4: Commit**

```bash
git add robot-platform-ui-admin/src/api/ai
git commit -m "feat(ui): add ai administration api clients"
```

---

### Task 2: Build Model Provider / Model page

**Files:**
- Create: `robot-platform-ui-admin/src/views/ai/model/index.vue`
- Create focused form components under `robot-platform-ui-admin/src/views/ai/model/components/`.
- Test: `robot-platform-ui-admin/src/views/ai/model/index.test.ts`

**Interfaces:**
- Provider types: Qwen, DeepSeek, Doubao.
- Model types: Chat, Realtime S2S, ASR, TTS, Embedding.

- [ ] **Step 1: Write component tests**

Verify:

- provider key field is write-only/password input;
- editing an existing provider shows only “已配置/未配置” state;
- leaving key blank on update preserves existing credential;
- model form filters/labels model type correctly.

- [ ] **Step 2: Implement page**

Use two tabs/sections: Providers and Models. Put provider-specific JSON configuration behind an “高级配置” editor/structured form; do not surface secrets.

- [ ] **Step 3: Run tests and commit**

```bash
cd robot-platform-ui-admin && pnpm test --run && pnpm type-check
git add robot-platform-ui-admin/src/views/ai/model
git commit -m "feat(ui): manage ai models and providers"
```

---

### Task 3: Build Prompt and Agent pages

**Files:**
- Create: `robot-platform-ui-admin/src/views/ai/prompt/index.vue`
- Create: `robot-platform-ui-admin/src/views/ai/agent/index.vue`
- Create Agent form components under `.../views/ai/agent/components/`.
- Test both pages.

**Interfaces:**
- Agent form maps `NATIVE`, `CASCADE`, `AUTO` to model fields.

- [ ] **Step 1: Write Agent form behavior tests**

Expected field behavior:

```text
NATIVE  -> require Realtime model; hide/disable cascade-only fields
CASCADE -> require ASR + Chat + TTS
AUTO    -> require both paths
```

Memory controls: mode + read/write toggles.

- [ ] **Step 2: Implement Prompt page**

List versions by prompt code, create new immutable versions, and show which Agent references a version when backend data exposes it.

- [ ] **Step 3: Implement Agent page**

Fields: name/code/description/System Prompt/model route/memory policy/status. Include Robot binding drawer/dialog and default-Agent toggle.

- [ ] **Step 4: Run tests and commit**

```bash
git add robot-platform-ui-admin/src/views/ai/prompt robot-platform-ui-admin/src/views/ai/agent
git commit -m "feat(ui): configure prompts and agents"
```

---

### Task 4: Add Robot detail Agent tab

**Files:**
- Modify the existing robot detail page under `robot-platform-ui-admin/src/views/robot/` after locating its current exact path.
- Create: focused `AgentBindingsTab.vue` next to the robot detail components.
- Test binding/default behavior.

**Interfaces:**
- Reads Agent bindings for the selected robot.
- Can bind/unbind and switch one default Agent.

- [ ] **Step 1: Locate existing robot detail route/component and write tests against its established pattern**

Do not invent a duplicate Robot details page.

- [ ] **Step 2: Implement Agent tab**

Show:

```text
默认智能体
已绑定智能体
状态
实时模式
```

Changing default must use the backend transactional binding API.

- [ ] **Step 3: Run tests and commit**

```bash
git add robot-platform-ui-admin/src/views/robot
git commit -m "feat(ui): manage robot agent bindings"
```

---

### Task 5: Build Conversation and Realtime Session observability pages

**Files:**
- Create: `robot-platform-ui-admin/src/views/ai/conversation/index.vue`
- Create: `robot-platform-ui-admin/src/views/ai/realtime/index.vue`
- Create trace/detail components.
- Backend if not already present: add read-only admin query controllers/services for conversations and realtime sessions.
- Tests for both backend query isolation and frontend rendering.

**Interfaces:**
- Conversation detail shows USER / ASSISTANT / TOOL_CALL / TOOL_RESULT in chronological order.
- Realtime session shows route, provider/model, timing, interrupt count, error code.

- [ ] **Step 1: Add/read-only backend page queries if missing**

Endpoints:

```text
GET /admin-api/ai/conversations
GET /admin-api/ai/conversations/{id}
GET /admin-api/ai/realtime-sessions
GET /admin-api/ai/realtime-sessions/{id}
```

All lookup paths include current tenant ID.

- [ ] **Step 2: Write frontend tests with real response shapes**

Verify the UI clearly distinguishes `NATIVE` and `CASCADE`, actual model, first-response latency, interrupt count and abnormal status.

- [ ] **Step 3: Implement pages and commit**

```bash
git add robot-platform-module-ai robot-platform-ui-admin/src/views/ai/conversation robot-platform-ui-admin/src/views/ai/realtime
git commit -m "feat(ai): add conversation and realtime observability"
```

---

### Task 6: Build Memory administration page

**Files:**
- Create: `robot-platform-ui-admin/src/views/ai/memory/index.vue`
- Test: `robot-platform-ui-admin/src/views/ai/memory/index.test.ts`

**Interfaces:**
- Filters: scope, memory type, member, robot, status.
- Actions: edit content/summary/importance/expiry; delete/invalidate.

- [ ] **Step 1: Write tests**

Verify member/robot IDs are displayed through resolved names when available, expiry and status are visible, and delete calls logical delete API.

- [ ] **Step 2: Implement page**

Do not expose internal extraction prompt or model chain by default; show source conversation link for audit.

- [ ] **Step 3: Run tests and commit**

```bash
git add robot-platform-ui-admin/src/views/ai/memory
git commit -m "feat(ui): manage agent memories"
```

---

### Task 7: Add menus/permissions and database seed entries

**Files:**
- Modify: `sql/mysql/robot-platform.sql` or the existing menu/permission seed file used by this repository.
- Possibly modify frontend route metadata only if routes are not backend-menu-driven.
- Test: backend permission seed contract or frontend route smoke test.

**Interfaces:**
- AI Center parent menu with six pages.
- Button permissions match backend strings from Plans 1 and 3.

- [ ] **Step 1: Add a seed contract test**

Assert every backend `@PreAuthorize` AI permission appears in menu/button permission seed data.

- [ ] **Step 2: Add menu entries without breaking tenant package/RBAC conventions**

Pages:

```text
智能体
Prompt
模型
对话记录
长期记忆
实时会话
```

- [ ] **Step 3: Run backend + frontend checks and commit**

```bash
git add sql/mysql robot-platform-ui-admin
git commit -m "feat(ai): add ai center permissions and menus"
```

---

### Task 8: Add backend integration tests for tenant isolation and trace integrity

**Files:**
- Create: `robot-platform-server/src/test/java/com/robot/platform/ai/AiTenantIsolationIntegrationTest.java`
- Create: `robot-platform-server/src/test/java/com/robot/platform/ai/AiRealtimeTraceIntegrationTest.java`
- Reuse existing Testcontainers/MySQL setup patterns.

**Interfaces:**
- Black-box service/controller tests against real MySQL schema.

- [ ] **Step 1: Test cross-tenant denial**

Create tenant A/B records for Agent, model, memory, conversation and assert A cannot query/update/delete B's IDs.

- [ ] **Step 2: Test trace lifecycle**

With fake provider adapter:

```text
session start -> user transcript -> assistant deltas -> interrupt -> second turn -> close
```

Assert persisted rows show correct actual model, route, prompt version, message order, interrupt count and no secret/raw audio.

- [ ] **Step 3: Run integration tests**

```bash
mvn -pl robot-platform-server -am -Dtest=AiTenantIsolationIntegrationTest,AiRealtimeTraceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add robot-platform-server/src/test
git commit -m "test(ai): verify tenant isolation and realtime trace"
```

---

### Task 9: Add opt-in live provider contract tests

**Files:**
- Create: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/live/QwenRealtimeLiveTest.java`
- Create: `.../DoubaoRealtimeLiveTest.java`
- Create: `.../DeepSeekChatLiveTest.java`
- Modify README/docs for environment variables; never commit values.

**Interfaces:**
- Live tests are disabled unless explicit flags/credentials are supplied.

- [ ] **Step 1: Gate tests**

Use environment assumptions such as:

```text
RUN_AI_PROVIDER_LIVE_TESTS=true
QWEN_API_KEY
DOUBAO_ACCESS_TOKEN
DOUBAO_APP_ID
DOUBAO_RESOURCE_ID
DEEPSEEK_API_KEY
```

- [ ] **Step 2: Implement minimal live checks**

Qwen/Doubao: establish session, send a short generated/sanitized PCM fixture, require transcript or audio response, close cleanly.

DeepSeek: stream a fixed non-sensitive prompt and require at least one text delta plus normal completion.

- [ ] **Step 3: Document commands and commit**

```bash
git add robot-platform-module-ai/src/test README.md docs
git commit -m "test(ai): add opt in provider live contracts"
```

---

### Task 10: Add RK3588 protocol simulator and full E2E script

**Files:**
- Create: `scripts/e2e/realtime-agent-client.py` or Java equivalent using repository-approved tooling.
- Create: `scripts/e2e/realtime-agent.sh`
- Add sanitized PCM fixture under `scripts/e2e/fixtures/`.
- Document usage.

**Interfaces:**
- Simulator speaks only platform `WSS /device-api/ai/realtime`, never provider protocols.

- [ ] **Step 1: Implement simulator state machine**

Flow:

```text
obtain/use device token -> WSS connect -> session.start -> binary PCM -> speech_stopped -> receive text/audio -> optional speech_started interrupt -> close
```

Print IDs, event types and latency; never print device/provider secrets.

- [ ] **Step 2: Add three selectable E2E modes**

```text
AGENT_CODE=qwen-native
AGENT_CODE=doubao-native
AGENT_CODE=deepseek-cascade
```

- [ ] **Step 3: Add memory scenario**

Identified member fixture says a harmless preference, waits for async extraction, then starts a second conversation and verifies the persisted memory can be retrieved through admin/test service evidence. Anonymous variant verifies no MEMBER memory row is created.

- [ ] **Step 4: Run release verification**

Backend:

```bash
mvn -pl robot-platform-server -am test
```

Frontend in supported Node/pnpm environment:

```bash
cd robot-platform-ui-admin
pnpm install --frozen-lockfile
pnpm type-check
pnpm test --run
```

E2E with mocked provider adapters must pass in CI/local without paid credentials. Live provider tests remain opt-in.

- [ ] **Step 5: Commit**

```bash
git add scripts README.md docs
git commit -m "test(ai): add realtime agent e2e simulator"
```

## Plan 4 Exit Criteria

- Six AI Center pages work against real backend APIs with no fake metrics/data.
- Robot detail can bind multiple Agents and select one default.
- Conversation, realtime trace and memory are inspectable by tenant admins.
- No provider secret is present in browser responses/state.
- Backend integration tests prove cross-tenant isolation and trace correctness.
- A vendor-neutral RK3588 simulator verifies native Qwen, native Doubao and DeepSeek cascade configurations.
- CI/local tests do not require paid provider credentials; live provider contracts are explicit opt-in checks.
