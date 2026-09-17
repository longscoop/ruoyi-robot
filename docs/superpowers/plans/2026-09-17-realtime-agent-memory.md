# Realtime Agent Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add identity-aware long-term memory extraction, policy, storage and retrieval without blocking realtime response or leaking memory across tenants/members/robots.

**Architecture:** Memory is not owned by an Agent. The runtime supplies authenticated tenant/robot identity plus a validated member identity to `MemoryRetriever`; completed turns enqueue asynchronous candidates to `MemoryExtractor`/`MemoryPolicy`, which writes `ai_memory`. SESSION context stays in runtime memory; long-term rows are MEMBER, MEMBER_ROBOT or ROBOT scoped.

**Tech Stack:** Java 17, Spring Boot, MyBatis Plus, MySQL, existing member/robot binding service, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-17-realtime-agent-design.md`

## Global Constraints

- Requires Plans 1 and 2.
- Tenant/robot identity is trusted only from device/app authentication context.
- Candidate `memberId` from RK3588 must be validated against `member_robot_binding` before member-scoped reads/writes.
- Anonymous/low-confidence users never write MEMBER or MEMBER_ROBOT memory.
- Long-term extraction is asynchronous and never blocks realtime response.
- Retrieval filters tenant/scope/status/expiry before relevance ranking.
- V1 does not require a vector DB.

---

### Task 1: Add `ai_memory` persistence and scope invariants

**Files:**
- Modify: `sql/mysql/robot-platform.sql`
- Create: `.../ai/memory/dal/dataobject/AiMemoryDO.java`
- Create: `.../ai/memory/dal/mysql/AiMemoryMapper.java`
- Test: `.../ai/memory/AiMemorySchemaContractTest.java`
- Test: `.../ai/memory/AiMemoryScopeValidatorTest.java`

**Interfaces:**
- Scopes: `MEMBER`, `MEMBER_ROBOT`, `ROBOT`.
- Types: `PROFILE`, `PREFERENCE`, `RELATION`, `HABIT`, `FACT`, `ENVIRONMENT`, `INSTRUCTION`.
- Status: `ACTIVE`, `SUPERSEDED`, `DELETED`.

- [ ] **Step 1: Write failing scope validator tests**

```java
assertThrows(IllegalArgumentException.class,
    () -> validator.validate("MEMBER", null, 10L));
assertThrows(IllegalArgumentException.class,
    () -> validator.validate("MEMBER_ROBOT", 20L, null));
assertDoesNotThrow(() -> validator.validate("ROBOT", null, 10L));
```

- [ ] **Step 2: Add table and indexes**

Required indexes:

```sql
KEY idx_ai_memory_member (`tenant_id`,`member_id`,`scope`,`status`,`expires_at`),
KEY idx_ai_memory_robot (`tenant_id`,`robot_id`,`scope`,`status`,`expires_at`),
KEY idx_ai_memory_source (`tenant_id`,`source_conversation_id`,`source_message_id`)
```

- [ ] **Step 3: Implement DO, mapper and scope validator**

Mapper reads must accept tenant ID explicitly; no unscoped `selectById` in service code.

- [ ] **Step 4: Run tests and commit**

```bash
mvn -pl robot-platform-module-ai -am test
git add sql/mysql/robot-platform.sql robot-platform-module-ai
git commit -m "feat(ai): add long term memory persistence"
```

---

### Task 2: Validate RK3588 member identity before memory access

**Files:**
- Create: `.../ai/memory/identity/ConversationIdentity.java`
- Create: `.../ai/memory/identity/ConversationIdentityResolver.java`
- Modify: `.../ai/realtime/runtime/RealtimeAgentRuntime.java`
- Test: `.../ai/memory/identity/ConversationIdentityResolverTest.java`

**Interfaces:**

```java
public record ConversationIdentity(long tenantId, long robotId, Long memberId,
        String identityType, double confidence, boolean memberMemoryAllowed) { }
```

- [ ] **Step 1: Write tests**

Cover:

1. `VOICEPRINT` confidence 0.92 + active binding => member accepted.
2. confidence below configured threshold => anonymous.
3. unbound member => anonymous/rejected candidate, never trusted.
4. cross-tenant member binding => anonymous/rejected.
5. `ANONYMOUS` => `memberId=null`.

- [ ] **Step 2: Implement resolver**

Use existing `MemberRobotAccessService`/binding query rather than duplicating member-robot authorization logic. Add configuration:

```text
robot.ai.memory.identity-confidence-threshold=0.85
```

- [ ] **Step 3: Integrate with session creation**

`ai_conversation.member_id` and `ai_realtime_session.member_id` receive only the validated member ID.

- [ ] **Step 4: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): validate conversation member identity"
```

---

### Task 3: Define memory service boundaries and tenant-safe retrieval

**Files:**
- Create: `.../ai/memory/service/MemoryStore.java`
- Create: `.../ai/memory/service/MemoryRetriever.java`
- Create: `.../ai/memory/service/MySqlMemoryStore.java`
- Create: `.../ai/memory/service/MySqlMemoryRetriever.java`
- Create: `.../ai/memory/service/MemoryQuery.java`
- Test: `.../ai/memory/MySqlMemoryRetrieverTest.java`

**Interfaces:**

```java
interface MemoryStore {
    long save(MemoryWrite write);
    void supersede(long tenantId, long memoryId, Long replacementId);
    void delete(long tenantId, long memoryId);
}
interface MemoryRetriever {
    List<MemorySnippet> retrieve(MemoryQuery query, int limit);
}
```

- [ ] **Step 1: Write retrieval boundary tests**

For a MEMBER query assert SQL/service filters are applied before ranking:

```text
tenantId = current tenant
memberId = validated member
scope in MEMBER, MEMBER_ROBOT as authorized
status = ACTIVE
expiresAt is null or > now
```

ROBOT memory may be included only for the current robot.

- [ ] **Step 2: Implement V1 ranking**

No vector dependency. Rank candidates by weighted score:

```text
text match / type match + importance + recency
```

Keep scoring deterministic and unit-tested. Return at most 8 snippets by default.

- [ ] **Step 3: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): retrieve tenant safe memories"
```

---

### Task 4: Add memory extractor contract and explicit memory commands

**Files:**
- Create: `.../ai/memory/extract/MemoryExtractor.java`
- Create: `.../ai/memory/extract/LlmMemoryExtractor.java`
- Create: `.../ai/memory/extract/MemoryCandidate.java`
- Create: `.../ai/memory/policy/MemoryDirectiveParser.java`
- Test: `.../ai/memory/policy/MemoryDirectiveParserTest.java`

**Interfaces:**
- Directives: `REMEMBER`, `DO_NOT_REMEMBER`, `FORGET`, `NORMAL`.
- Extractor consumes completed USER/ASSISTANT turn plus validated identity and returns structured candidates, never raw SQL/DOs.

- [ ] **Step 1: Write directive tests**

Chinese examples:

```text
“记住，我喝咖啡少糖” -> REMEMBER
“别记这个” -> DO_NOT_REMEMBER
“忘掉我刚才说的咖啡偏好” -> FORGET
“讲个笑话” -> NORMAL
```

Include equivalent simple English phrases if supported by the product prompt.

- [ ] **Step 2: Implement deterministic directive detection first**

Explicit user commands take precedence over extractor model output.

- [ ] **Step 3: Implement `LlmMemoryExtractor`**

Use the configured `MEMORY_EXTRACT` prompt and a `ChatModelClient`. Require strict JSON output:

```json
{
  "candidates": [
    {
      "scope":"MEMBER",
      "memoryType":"PREFERENCE",
      "content":"用户喝咖啡偏好少糖",
      "importance":0.82,
      "confidence":0.96,
      "expiresAt":null
    }
  ]
}
```

Malformed output returns no candidate and records an extraction error metric; it must not affect the user reply.

- [ ] **Step 4: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): extract structured memory candidates"
```

---

### Task 5: Implement MemoryPolicy dedupe/merge/expiry rules

**Files:**
- Create: `.../ai/memory/policy/MemoryPolicy.java`
- Create: `.../ai/memory/policy/DefaultMemoryPolicy.java`
- Create: `.../ai/memory/policy/MemoryDecision.java`
- Test: `.../ai/memory/policy/DefaultMemoryPolicyTest.java`

**Interfaces:**
- Decisions: `IGNORE`, `CREATE`, `REPLACE`, `DELETE_MATCHES`.

- [ ] **Step 1: Write policy tests**

Cover:

- anonymous + MEMBER candidate => IGNORE;
- low confidence => IGNORE;
- explicit REMEMBER preference => CREATE even if importance is moderate;
- exact/near duplicate active preference => REPLACE or refresh existing, not duplicate row;
- temporary event (“今天下午三点开会”) => require finite `expiresAt`;
- casual greeting/joke => IGNORE;
- explicit FORGET => `DELETE_MATCHES` within same authorized scope only.

- [ ] **Step 2: Implement deterministic V1 thresholds**

Start with constants in configuration:

```text
minConfidence=0.75
minImportance=0.60
```

Explicit REMEMBER bypasses importance threshold but not identity/tenant authorization.

- [ ] **Step 3: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): enforce memory retention policy"
```

---

### Task 6: Run extraction asynchronously after completed turns

**Files:**
- Create: `.../ai/memory/pipeline/MemoryPipeline.java`
- Create: `.../ai/memory/pipeline/AsyncMemoryPipeline.java`
- Modify: `.../ai/realtime/runtime/RealtimeAgentRuntime.java`
- Test: `.../ai/memory/pipeline/AsyncMemoryPipelineTest.java`

**Interfaces:**
- `void submit(CompletedTurn turn, ConversationIdentity identity, AiAgentConfig agent)` returns immediately.

- [ ] **Step 1: Write non-blocking test**

Use a latch-blocked fake extractor and verify `submit()` returns without waiting for extractor completion.

- [ ] **Step 2: Configure bounded executor**

Use a named executor with bounded queue and rejection logging/metric. Do not create one thread per conversation.

- [ ] **Step 3: Trigger pipeline only after finalized turn**

Do not extract from partial ASR or partial assistant deltas. On `assistant.done`, submit the finalized USER/ASSISTANT pair.

- [ ] **Step 4: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): process memories asynchronously"
```

---

### Task 7: Inject relevant memories into Agent context

**Files:**
- Create: `.../ai/memory/context/MemoryContextBuilder.java`
- Modify native/cascade session setup in runtime.
- Test: `.../ai/memory/context/MemoryContextBuilderTest.java`

**Interfaces:**
- `String buildContext(ConversationIdentity identity, AiAgentConfig agent, String currentUserText)`

- [ ] **Step 1: Write context tests**

Assert:

- memory disabled => empty context;
- anonymous => no MEMBER/MEMBER_ROBOT memory;
- identified member => relevant member + member-robot + robot snippets;
- no more than configured Top K;
- expired/deleted memory never appears.

- [ ] **Step 2: Use a delimited system context block**

Example:

```text
<retrieved_memory>
- 用户喝咖啡偏好少糖。
- 这台机器人充电桩位于客厅电视柜旁。
</retrieved_memory>
```

The Agent's authored System Prompt remains separately versioned.

- [ ] **Step 3: Apply to both Native and Cascade routes**

For native providers, merge into the provider session instructions; for cascade, include it in the chat request system context.

- [ ] **Step 4: Run tests and commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): recall memory into agent context"
```

---

### Task 8: Add admin memory query/edit/delete APIs

**Files:**
- Create: `.../ai/memory/controller/admin/AiMemoryAdminController.java`
- Create request/response VOs.
- Test: `.../ai/memory/AiMemoryAdminControllerTest.java`

**Interfaces:**
- `GET /admin-api/ai/memories`
- `PUT /admin-api/ai/memories/{id}` only for editable fields `content`, `summary`, `importance`, `expiresAt`.
- `DELETE /admin-api/ai/memories/{id}` performs logical deletion/invalidating, not an unscoped physical delete.

- [ ] **Step 1: Test tenant-safe access**

Cross-tenant ID lookup returns not found/forbidden and never reveals row existence.

- [ ] **Step 2: Implement controller/service methods**

Permission names:

```text
ai:memory:query
ai:memory:update
ai:memory:delete
```

- [ ] **Step 3: Run all AI tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): manage long term memories"
```

## Plan 3 Exit Criteria

- Member identity is validated before any member-scoped memory operation.
- SESSION context is runtime-only; persistent memory is MEMBER/MEMBER_ROBOT/ROBOT.
- Explicit remember/do-not-remember/forget commands behave deterministically.
- Automatic extraction is asynchronous and cannot delay realtime reply.
- Retrieval applies authorization filters before ranking and returns a small Top K.
- Anonymous conversations never write personal memory.
- Admin can inspect/update/delete only tenant-owned memories.
