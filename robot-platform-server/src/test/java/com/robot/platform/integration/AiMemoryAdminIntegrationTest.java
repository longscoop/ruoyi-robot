package com.robot.platform.integration;

import com.robot.platform.ai.memory.service.AiMemoryAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiMemoryAdminIntegrationTest extends AbstractRobotPlatformIntegrationTest {

    private static final long FIRST_TENANT = 999001L;
    private static final long SECOND_TENANT = 999002L;

    @Autowired private DataSource dataSource;
    @Autowired private AiMemoryAdminService memories;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    void aiRuntimeAdminRoutesHaveExactlyOneAdminPrefix() {
        Set<String> paths = mappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());
        assertThat(paths).contains("/admin-api/ai/memories", "/admin-api/ai/realtime-sessions",
                "/admin-api/ai/digital-humans");
        assertThat(paths).noneMatch(path -> path.startsWith("/admin-api/admin-api/ai/"));
    }

    @Test
    void adminCanListEditAndInvalidateOnlyItsOwnMemories() {
        var jdbc = new JdbcTemplate(dataSource);
        try {
            insert(jdbc, FIRST_TENANT, "first tenant fact");
            insert(jdbc, SECOND_TENANT, "second tenant fact");
            long firstId = jdbc.queryForObject(
                    "SELECT id FROM ai_memory WHERE tenant_id = ? AND content = ?",
                    Long.class, FIRST_TENANT, "first tenant fact");

            assertThat(memories.list(FIRST_TENANT)).extracting("content")
                    .contains("first tenant fact").doesNotContain("second tenant fact");
            assertThatThrownBy(() -> memories.update(SECOND_TENANT, firstId,
                    "stolen", null, new BigDecimal("0.7"), null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> memories.delete(SECOND_TENANT, firstId))
                    .isInstanceOf(IllegalArgumentException.class);

            memories.update(FIRST_TENANT, firstId, "corrected fact", "edited",
                    new BigDecimal("0.7"), null);
            assertThat(memories.list(FIRST_TENANT)).anySatisfy(row -> {
                assertThat(row.getContent()).isEqualTo("corrected fact");
                assertThat(row.getSummary()).isEqualTo("edited");
            });
            memories.delete(FIRST_TENANT, firstId);
            assertThat(jdbc.queryForObject("SELECT status FROM ai_memory WHERE id = ?",
                    String.class, firstId)).isEqualTo("DELETED");
            assertThat(memories.list(FIRST_TENANT)).extracting("content")
                    .doesNotContain("corrected fact");
        } finally {
            jdbc.update("DELETE FROM ai_memory WHERE tenant_id IN (?, ?)", FIRST_TENANT, SECOND_TENANT);
        }
    }

    private static void insert(JdbcTemplate jdbc, long tenantId, String content) {
        jdbc.update("""
                INSERT INTO ai_memory (tenant_id, scope, robot_id, memory_type, content,
                  importance, confidence, first_observed_at, last_observed_at, status)
                VALUES (?, 'ROBOT', 1, 'FACT', ?, 0.5, 0.8, NOW(3), NOW(3), 'ACTIVE')
                """, tenantId, content);
    }
}
