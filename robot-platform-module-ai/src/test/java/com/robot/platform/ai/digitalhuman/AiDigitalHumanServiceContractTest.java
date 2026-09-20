package com.robot.platform.ai.digitalhuman;
import com.robot.platform.ai.digitalhuman.service.AiDigitalHumanServiceImpl;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class AiDigitalHumanServiceContractTest {
 @Test void serviceEnforcesTenantAgentAndTtsBoundaries() throws Exception {String s=Files.readString(Path.of("src/main/java/com/robot/platform/ai/digitalhuman/service/AiDigitalHumanServiceImpl.java"));assertTrue(s.contains("selectByIdAndTenantId(c.agentId(),c.tenantId())"));assertTrue(s.contains("\"TTS\".equals(m.getModelType())"));assertTrue(s.contains("TenantContextHolder.getRequiredTenantId()"));assertTrue(s.contains("duplicate digital human state"));}
}
