package com.robot.platform.integration;
import com.robot.platform.ai.admin.AiDisplayOptionMapper;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

class AiAdminExperienceIntegrationTest extends AbstractRobotPlatformIntegrationTest {
    @Autowired AiPromptService prompts;
    @Autowired AiAgentService agents;
    @Autowired AiDisplayOptionMapper names;
    @Autowired DataSource dataSource;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappings;
    @Test void roleCreationEditingAndDisplayNamesRespectTenantBoundaries() {
        assertThat(requestMappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream()))
                .contains("/admin-api/ai/display-options", "/admin-api/ai/prompts/{id}");
        var jdbc = new JdbcTemplate(dataSource);
        long tenant = 999031;
        TenantContextHolder.setTenantId(tenant);
        try {
            jdbc.update("INSERT INTO ai_model_provider (tenant_id,name,code,provider_type,base_url) VALUES (?, '测试服务商', 'test-provider', 'QWEN', 'wss://example.test')", tenant);
            long providerId = jdbc.queryForObject("SELECT id FROM ai_model_provider WHERE tenant_id=? AND code='test-provider'", Long.class, tenant);
            jdbc.update("INSERT INTO ai_model (tenant_id,provider_id,name,model_code,model_type) VALUES (?,?,'测试实时模型','test-realtime','REALTIME_S2S')", tenant, providerId);
            long modelId = jdbc.queryForObject("SELECT id FROM ai_model WHERE tenant_id=? AND model_code='test-realtime'", Long.class, tenant);
            var role = prompts.create(new AiPromptService.CreatePromptCommand(tenant, "家庭助手角色", null, "SYSTEM", "old", "ENABLED"));
            jdbc.update("INSERT INTO ai_agent (tenant_id,name,code,system_prompt_id,realtime_mode,realtime_model_id) VALUES (?, '家庭助手', 'test-role-agent', ?, 'NATIVE',?)", tenant, role.getId(), modelId);
            jdbc.update("INSERT INTO ai_agent (tenant_id,name,code,system_prompt_id,realtime_mode) VALUES (?, '其他租户助手', 'test-role-agent', ?, 'NATIVE')", tenant + 1, role.getId());
            long agentId = jdbc.queryForObject("SELECT id FROM ai_agent WHERE tenant_id=? AND code='test-role-agent'", Long.class, tenant);
            prompts.update(tenant, role.getId(), new AiPromptService.CreatePromptCommand(tenant, "家庭助手角色", null, "SYSTEM", "updated role", "ENABLED"));
            assertThat(agents.getResolvedConfig(tenant, agentId).systemPrompt()).isEqualTo("updated role");
            assertThat(prompts.get(tenant, role.getId()).getVersion()).isEqualTo(2);
            assertThat(names.agents(tenant)).extracting(AiDisplayOptionMapper.Option::name).contains("家庭助手").doesNotContain("其他租户助手");
            TenantContextHolder.setTenantId(tenant + 1);
            assertThatThrownBy(() -> prompts.update(tenant + 1, role.getId(), new AiPromptService.CreatePromptCommand(tenant + 1, "x", null, "SYSTEM", "x", "ENABLED"))).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.update("DELETE FROM ai_agent WHERE tenant_id IN (?,?)", tenant, tenant + 1);
            jdbc.update("DELETE FROM ai_prompt WHERE tenant_id=?", tenant);
            jdbc.update("DELETE FROM ai_model WHERE tenant_id=?", tenant);
            jdbc.update("DELETE FROM ai_model_provider WHERE tenant_id=?", tenant);
            TenantContextHolder.clear();
        }
    }
}
