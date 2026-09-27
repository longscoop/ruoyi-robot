package com.robot.platform.ai.digitalhuman;
import org.junit.jupiter.api.Test;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class AiDigitalHumanAdminContractTest {@Test void apiNeverExposesProviderSecrets() throws Exception {String s=Files.readString(Path.of("src/main/java/com/robot/platform/ai/digitalhuman/controller/admin/AiDigitalHumanAdminController.java"));assertTrue(s.contains("/admin-api/ai/digital-humans"));assertFalse(s.contains("apiKey"));assertFalse(s.contains("baseUrl"));assertTrue(s.contains("ai:digital-human:preview"));}}
