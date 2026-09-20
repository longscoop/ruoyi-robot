package com.robot.platform.ai.digitalhuman;

import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanActionDO;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.digitalhuman.dal.mysql.AiDigitalHumanActionMapper;
import com.robot.platform.ai.digitalhuman.dal.mysql.AiDigitalHumanMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDigitalHumanDomainContractTest {

    @Test
    void schemaDefinesTenantScopedDigitalHumanConstraints() throws Exception {
        String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `ai_digital_human`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ai_digital_human_tenant_code` (`tenant_id`,`code`)"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `ai_digital_human_action`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ai_digital_human_action_state` (`tenant_id`,`digital_human_id`,`state`)"));
    }

    @Test
    void dataObjectsUseExpectedTables() {
        assertEquals("ai_digital_human", AiDigitalHumanDO.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertEquals("ai_digital_human_action", AiDigitalHumanActionDO.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void mappersExposeTenantScopedReadsAndLogicalDelete() throws Exception {
        String humanMapper = Files.readString(Path.of("src/main/java/com/robot/platform/ai/digitalhuman/dal/mysql/AiDigitalHumanMapper.java"));
        assertTrue(humanMapper.contains("tenant_id = #{tenantId}"));
        assertTrue(humanMapper.contains("deleted = 0"));
        assertTrue(humanMapper.contains("logicalDeleteByIdAndTenantId"));

        String actionMapper = Files.readString(Path.of("src/main/java/com/robot/platform/ai/digitalhuman/dal/mysql/AiDigitalHumanActionMapper.java"));
        assertTrue(actionMapper.contains("tenant_id = #{tenantId}"));
        assertTrue(actionMapper.contains("digital_human_id = #{digitalHumanId}"));
        assertTrue(actionMapper.contains("deleted = 0"));
    }
}
