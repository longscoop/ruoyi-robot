package com.robot.platform.ai.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMemorySchemaContractTest {

    @Test
    void memoryTableContainsRequiredTenantScopedIndexesAndConstraints() throws Exception {
        String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `ai_memory`"));
        assertTrue(sql.contains("KEY `idx_ai_memory_member` (`tenant_id`,`member_id`,`scope`,`status`,`expires_at`)"));
        assertTrue(sql.contains("KEY `idx_ai_memory_robot` (`tenant_id`,`robot_id`,`scope`,`status`,`expires_at`)"));
        assertTrue(sql.contains("KEY `idx_ai_memory_source` (`tenant_id`,`source_conversation_id`,`source_message_id`)"));
        assertTrue(sql.contains("CONSTRAINT `chk_ai_memory_scope` CHECK"));
        assertTrue(sql.contains("CONSTRAINT `chk_ai_memory_status` CHECK"));
    }
}
