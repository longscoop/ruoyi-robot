package com.robot.platform.ai.schema;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCoreSchemaContractTest {

    @Test
    void coreAiTablesArePresent() throws Exception {
        String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));

        for (String table : List.of(
                "ai_model_provider",
                "ai_model",
                "ai_prompt",
                "ai_agent",
                "ai_agent_robot")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`")
                    || sql.contains("CREATE TABLE `" + table + "`"), table);
        }
        assertTrue(sql.contains("`api_key_ciphertext`"));
        assertFalse(sql.contains("`api_key` varchar"));
    }
}
