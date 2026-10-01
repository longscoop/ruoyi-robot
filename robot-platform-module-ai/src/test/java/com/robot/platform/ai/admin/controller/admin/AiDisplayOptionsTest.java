package com.robot.platform.ai.admin.controller.admin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AiDisplayOptionsTest {
    @Test void offersProviderVoicesAndTenantConfiguredCustomVoices() {
        var voices = AiDisplayOptionsController.voices("QWEN", "qwen3-tts-flash-realtime",
                "{\"voices\":[{\"id\":\"custom-1\",\"name\":\"自定义声线\"}],\"voice\":\"Cherry\"}");
        assertEquals(1, voices.stream().filter(v -> v.id().equals("Cherry")).count());
        assertTrue(voices.stream().anyMatch(v -> v.id().equals("custom-1") && v.name().equals("自定义声线")));
    }
    @Test void doesNotOfferQwenVoicesForOtherProvidersOrCloneOnlyModels() {
        assertTrue(AiDisplayOptionsController.voices("DOUBAO", "qwen3-tts-flash-realtime", null).isEmpty());
        assertTrue(AiDisplayOptionsController.voices("QWEN", "qwen3-tts-vc-realtime", null).isEmpty());
    }
}
