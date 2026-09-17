# Realtime Agent Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the AI module, tenant-safe provider/model/prompt/agent configuration, and many-to-many Agent↔Robot bindings that later realtime and memory work can depend on.

**Architecture:** Keep all AI behavior in a single `robot-platform-module-ai` Maven module and follow the repository's existing Controller/Service/DAL/MyBatis patterns. Provider credentials are encrypted server-side; Agent references model and prompt records by ID and never embeds provider secrets.

**Tech Stack:** Java 17, Spring Boot 3.5.15, MyBatis Plus, MySQL 8.4, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-17-realtime-agent-design.md`

## Global Constraints

- Use `robot-platform-*` module names and `com.robot.platform` packages.
- Every AI business row is tenant-scoped; never trust tenant IDs from HTTP request bodies.
- Agent and Robot are many-to-many; do not add `robot.agent_id`.
- Provider secrets are encrypted at rest and never returned to the UI or logged.
- Qwen, DeepSeek and Doubao are the initial provider types.
- Keep this module independent of realtime transport details; realtime is Plan 2.

---

### Task 1: Enable and scaffold `robot-platform-module-ai`

**Files:**
- Modify: `pom.xml`
- Modify: `robot-platform-server/pom.xml`
- Create: `robot-platform-module-ai/pom.xml`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/AiModuleConfiguration.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/AiModuleSmokeTest.java`

**Interfaces:**
- Consumes: existing tenant, security, MyBatis and device/robot modules.
- Produces: a buildable `robot-platform-module-ai` artifact included by `robot-platform-server`.

- [ ] **Step 1: Write the module smoke test**

```java
package com.robot.platform.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiModuleSmokeTest {
    @Test void configurationTypeExists() {
        assertNotNull(new AiModuleConfiguration());
    }
}
```

- [ ] **Step 2: Run it and verify the module is not yet buildable**

Run: `mvn -pl robot-platform-module-ai -am -Dtest=AiModuleSmokeTest test`

Expected: Maven fails because the AI module is not yet part of the reactor.

- [ ] **Step 3: Add the module and dependencies**

In the root `pom.xml`, enable:

```xml
<module>robot-platform-module-ai</module>
```

In `robot-platform-server/pom.xml`, add an active dependency:

```xml
<dependency>
    <groupId>com.robot.platform</groupId>
    <artifactId>robot-platform-module-ai</artifactId>
    <version>${revision}</version>
</dependency>
```

Create `robot-platform-module-ai/pom.xml` with dependencies on:

```xml
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-module-tenant</artifactId></dependency>
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-module-member</artifactId></dependency>
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-module-device</artifactId></dependency>
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-module-robot</artifactId></dependency>
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-framework-security</artifactId></dependency>
<dependency><groupId>com.robot.platform</groupId><artifactId>robot-platform-spring-boot-starter-mybatis</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
```

Create:

```java
package com.robot.platform.ai;

import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiModuleConfiguration { }
```

- [ ] **Step 4: Run the smoke test**

Run: `mvn -pl robot-platform-module-ai -am -Dtest=AiModuleSmokeTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add pom.xml robot-platform-server/pom.xml robot-platform-module-ai
git commit -m "feat(ai): enable ai module"
```

---

### Task 2: Add provider/model/prompt/agent/binding schema and persistence types

**Files:**
- Modify: `sql/mysql/robot-platform.sql`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/dal/dataobject/AiModelProviderDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/dal/dataobject/AiModelDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/prompt/dal/dataobject/AiPromptDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/dal/dataobject/AiAgentDO.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/dal/dataobject/AiAgentRobotDO.java`
- Create corresponding MyBatis mapper interfaces under each domain's `dal/mysql/` package.
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/schema/AiCoreSchemaContractTest.java`

**Interfaces:**
- Produces tables: `ai_model_provider`, `ai_model`, `ai_prompt`, `ai_agent`, `ai_agent_robot`.
- All DOs include `tenantId`; provider secret field is `apiKeyCiphertext` only.

- [ ] **Step 1: Write a schema contract test**

Read `sql/mysql/robot-platform.sql` and assert the required table/column names exist:

```java
@Test void coreAiTablesArePresent() throws Exception {
    String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));
    for (String table : List.of("ai_model_provider", "ai_model", "ai_prompt", "ai_agent", "ai_agent_robot")) {
        assertTrue(sql.contains("CREATE TABLE `" + table + "`"), table);
    }
    assertTrue(sql.contains("`api_key_ciphertext`"));
    assertFalse(sql.contains("`api_key` varchar"));
}
```

- [ ] **Step 2: Run the test and verify failure**

Run: `mvn -pl robot-platform-module-ai -Dtest=AiCoreSchemaContractTest test`

Expected: FAIL because the tables do not exist.

- [ ] **Step 3: Add the five tables**

Use the repository's existing MySQL conventions for bigint IDs, audit columns and logical deletion. Add unique constraints at minimum:

```sql
UNIQUE KEY uk_ai_provider_tenant_code (`tenant_id`,`code`),
UNIQUE KEY uk_ai_model_tenant_provider_code (`tenant_id`,`provider_id`,`model_code`),
UNIQUE KEY uk_ai_prompt_tenant_code_version (`tenant_id`,`code`,`version`),
UNIQUE KEY uk_ai_agent_tenant_code (`tenant_id`,`code`),
UNIQUE KEY uk_ai_agent_robot (`tenant_id`,`agent_id`,`robot_id`)
```

Add an index supporting the default-agent lookup:

```sql
KEY idx_ai_agent_robot_default (`tenant_id`,`robot_id`,`is_default`,`status`)
```

- [ ] **Step 4: Create DOs and mappers**

Follow existing `BaseDO` style. Example:

```java
@TableName("ai_model_provider")
@Data @EqualsAndHashCode(callSuper = true)
public class AiModelProviderDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String providerType;
    private String baseUrl;
    private String apiKeyCiphertext;
    private String configJson;
    private String status;
}
```

Each mapper must expose explicit tenant-qualified reads, e.g.:

```java
AiAgentDO selectByIdAndTenantId(long id, long tenantId);
AiAgentDO selectByCodeAndTenantId(String code, long tenantId);
List<AiAgentRobotDO> selectByRobot(long tenantId, long robotId);
```

- [ ] **Step 5: Run tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add sql/mysql/robot-platform.sql robot-platform-module-ai
git commit -m "feat(ai): add core ai persistence model"
```

---

### Task 3: Encrypt provider credentials and implement provider/model services

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/security/AiSecretCipher.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/security/AesGcmAiSecretCipher.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/service/AiModelProviderService.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/service/AiModelProviderServiceImpl.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/service/AiModelService.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/model/service/AiModelServiceImpl.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/model/AesGcmAiSecretCipherTest.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/model/AiModelProviderServiceTest.java`

**Interfaces:**
- `String AiSecretCipher.encrypt(String plaintext)`
- `String AiSecretCipher.decrypt(String ciphertext)`
- Provider service accepts plaintext only on create/update command and persists ciphertext.
- Provider response DTO never exposes decrypted secret.

- [ ] **Step 1: Write cipher tests**

```java
@Test void roundTripsAndUsesRandomNonce() {
    var cipher = new AesGcmAiSecretCipher(Base64.getDecoder().decode(TEST_KEY));
    String a = cipher.encrypt("secret");
    String b = cipher.encrypt("secret");
    assertNotEquals(a, b);
    assertEquals("secret", cipher.decrypt(a));
}
```

- [ ] **Step 2: Verify failure**

Run: `mvn -pl robot-platform-module-ai -Dtest=AesGcmAiSecretCipherTest test`

Expected: FAIL because the cipher is absent.

- [ ] **Step 3: Implement AES-256-GCM**

Use `AES/GCM/NoPadding`, a fresh 12-byte nonce per encryption, 128-bit tag, and encode `version|nonce|ciphertext` as Base64. Load the production 32-byte key from property `robot.ai.secret-key-base64`, whose deployment value comes from environment variable `ROBOT_AI_SECRET_KEY_BASE64`.

- [ ] **Step 4: Write provider service tests**

Mock the mapper and verify:

```java
verify(mapper).insert(argThat(row ->
    !"plain-key".equals(row.getApiKeyCiphertext()) && row.getTenantId().equals(1L)));
```

Also test that tenant mismatch is rejected before mapper mutation.

- [ ] **Step 5: Implement services**

Use command records such as:

```java
public record CreateProviderCommand(long tenantId, String name, String code,
        String providerType, String baseUrl, String apiKey, String configJson) { }
```

Supported `providerType`: `QWEN`, `DEEPSEEK`, `DOUBAO`.

Model service validates `modelType` as one of `CHAT`, `REALTIME_S2S`, `ASR`, `TTS`, `EMBEDDING` and verifies provider ownership by tenant.

- [ ] **Step 6: Run tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): manage encrypted model providers"
```

---

### Task 4: Implement Prompt and Agent services

**Files:**
- Create Prompt service classes under `.../ai/prompt/service/`.
- Create Agent service classes under `.../ai/agent/service/`.
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/service/AiAgentConfig.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/agent/AiAgentServiceTest.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/prompt/AiPromptServiceTest.java`

**Interfaces:**
- `AiPromptDO create(CreatePromptCommand command)`; prompt versions are immutable after creation.
- `AiAgentConfig getResolvedConfig(long tenantId, long agentId)` returns the exact model IDs, prompt ID/version and memory policy needed by runtime.

- [ ] **Step 1: Write failing tests for prompt versioning**

Assert that creating the same `(tenant, code)` again creates `version + 1`, and an Agent keeps its explicit `systemPromptId` until updated.

- [ ] **Step 2: Write failing tests for Agent model capability validation**

Examples:

```java
assertThrows(ServiceException.class, () -> service.create(
    commandWithRealtimeMode("NATIVE", chatOnlyModelId)));
```

Rules:

- `NATIVE` requires `realtime_model_id` with `REALTIME_S2S`.
- `CASCADE` requires `conversation_model_id`, `asr_model_id`, `tts_model_id` with matching model types.
- `AUTO` requires a valid native path and valid cascade path.

- [ ] **Step 3: Implement minimal Prompt and Agent services**

Define:

```java
public record AiAgentConfig(long agentId, long tenantId, String code, String systemPrompt,
    long promptId, int promptVersion, String realtimeMode,
    Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
    String memoryMode, boolean memoryReadEnabled, boolean memoryWriteEnabled) { }
```

Resolve all referenced rows with the same tenant ID.

- [ ] **Step 4: Run tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): add prompt and agent configuration"
```

---

### Task 5: Implement tenant-safe Agent↔Robot binding

**Files:**
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/service/AiAgentRobotBindingService.java`
- Create: `robot-platform-module-ai/src/main/java/com/robot/platform/ai/agent/service/AiAgentRobotBindingServiceImpl.java`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/agent/AiAgentRobotBindingServiceTest.java`

**Interfaces:**
- `long bind(long tenantId, long agentId, long robotId, boolean defaultAgent)`
- `AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode)`
- `AiAgentDO requireDefaultAgent(long tenantId, long robotId)`

- [ ] **Step 1: Write tests**

Cover:

1. Cross-tenant Agent rejected.
2. Cross-tenant Robot rejected through existing robot/device ownership verifier.
3. Setting a new default clears the previous default in one transaction.
4. Agent code not bound to the Robot is rejected.

- [ ] **Step 2: Verify failure**

Run: `mvn -pl robot-platform-module-ai -Dtest=AiAgentRobotBindingServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement binding service**

Use a transaction for default switching:

```java
@Transactional(rollbackFor = Exception.class)
public long bind(long tenantId, long agentId, long robotId, boolean makeDefault) {
    requireTenant(tenantId);
    requireOwnedAgent(tenantId, agentId);
    robotAccess.requireOwnedByTenant(tenantId, robotId);
    if (makeDefault) mapper.clearDefault(tenantId, robotId);
    return upsertBinding(tenantId, agentId, robotId, makeDefault);
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): bind agents to robots"
```

---

### Task 6: Add admin APIs for Provider, Model, Prompt and Agent

**Files:**
- Create admin controllers under:
  - `.../ai/model/controller/admin/`
  - `.../ai/prompt/controller/admin/`
  - `.../ai/agent/controller/admin/`
- Test: `robot-platform-module-ai/src/test/java/com/robot/platform/ai/controller/AiAdminControllerTest.java`

**Interfaces:**
- `/admin-api/ai/providers`
- `/admin-api/ai/models`
- `/admin-api/ai/prompts`
- `/admin-api/ai/agents`
- `/admin-api/ai/agents/{agentId}/robots`

- [ ] **Step 1: Write MVC/controller tests**

Assert controller methods obtain tenant exclusively from:

```java
TenantContextHolder.getRequiredTenantId()
```

and response DTOs expose only `apiKeyConfigured: boolean`, never ciphertext or plaintext.

- [ ] **Step 2: Implement controllers following existing `CommonResult` pattern**

Permission names:

```text
ai:provider:query/create/update/delete
ai:model:query/create/update/delete
ai:prompt:query/create
ai:agent:query/create/update/delete/bind
```

- [ ] **Step 3: Run the AI module test suite**

Run: `mvn -pl robot-platform-module-ai -am test`

Expected: PASS.

- [ ] **Step 4: Run server compilation**

Run: `mvn -pl robot-platform-server -am -DskipTests package`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add robot-platform-module-ai
git commit -m "feat(ai): expose agent administration api"
```

## Plan 1 Exit Criteria

- `robot-platform-module-ai` is part of the Maven reactor and server.
- Five core tables exist and all reads/writes are tenant-qualified.
- Provider secrets are encrypted and never returned.
- Qwen, DeepSeek and Doubao provider/model records can be configured.
- Prompt versions and Agent configurations are persisted.
- Robot can bind multiple Agents and exactly one default Agent.
- `mvn -pl robot-platform-module-ai -am test` passes.
