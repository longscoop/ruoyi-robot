package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeModelRouterTest {

    private final RealtimeModelRouter router = new RealtimeModelRouter();

    @Test
    void nativeModeRequiresNativeRoute() {
        RealtimeRoute route = router.route(agent("NATIVE"),
                capabilities(true, true, false, false, false));

        assertEquals(RealtimeRoute.Mode.NATIVE, route.mode());
        assertEquals("AGENT_NATIVE", route.routeReason());
        assertEquals(302L, route.realtimeModelId());
    }

    @Test
    void cascadeModeRequiresCompleteCascadeRoute() {
        RealtimeRoute route = router.route(agent("CASCADE"),
                capabilities(true, true, false, false, false));

        assertEquals(RealtimeRoute.Mode.CASCADE, route.mode());
        assertEquals("AGENT_CASCADE", route.routeReason());
        assertEquals(301L, route.conversationModelId());
        assertEquals(303L, route.asrModelId());
        assertEquals(304L, route.ttsModelId());
    }

    @Test
    void autoUsesNativeForOrdinaryRealtimeConversation() {
        RealtimeRoute route = router.route(agent("AUTO"),
                capabilities(true, true, false, false, false));

        assertEquals(RealtimeRoute.Mode.NATIVE, route.mode());
        assertEquals("AUTO_NATIVE", route.routeReason());
    }

    @Test
    void autoFallsBackToCascadeForChatOnlyCapability() {
        RealtimeRoute route = router.route(agent("AUTO"),
                capabilities(true, true, false, true, false));

        assertEquals(RealtimeRoute.Mode.CASCADE, route.mode());
        assertEquals("AUTO_CAPABILITY_FALLBACK", route.routeReason());
    }

    @Test
    void autoFallsBackToCascadeWhenToolPathIsNotSupportedNatively() {
        RealtimeRoute route = router.route(agent("AUTO"),
                capabilities(true, true, false, false, true));

        assertEquals(RealtimeRoute.Mode.CASCADE, route.mode());
        assertEquals("AUTO_CAPABILITY_FALLBACK", route.routeReason());
    }

    @Test
    void autoKeepsNativeWhenToolPathIsSupportedNatively() {
        RealtimeRoute route = router.route(agent("AUTO"),
                capabilities(true, true, true, false, true));

        assertEquals(RealtimeRoute.Mode.NATIVE, route.mode());
        assertEquals("AUTO_NATIVE", route.routeReason());
    }

    @Test
    void autoFallsBackWhenNativeCapabilityIsUnavailable() {
        RealtimeRoute route = router.route(agent("AUTO"),
                capabilities(false, true, false, false, false));

        assertEquals(RealtimeRoute.Mode.CASCADE, route.mode());
        assertEquals("AUTO_CAPABILITY_FALLBACK", route.routeReason());
    }

    @Test
    void nativeAndAutoRejectUnavailableRequiredRoutes() {
        assertThrows(IllegalStateException.class,
                () -> router.route(agent("NATIVE"), capabilities(false, true, false, false, false)));
        assertThrows(IllegalStateException.class,
                () -> router.route(agent("AUTO"), capabilities(false, false, false, false, false)));
    }

    private static RealtimeModelRouter.ModelCapabilities capabilities(
            boolean nativeSupported, boolean cascadeSupported, boolean nativeToolCallingSupported,
            boolean chatOnlyRequired, boolean toolPathRequired) {
        return new RealtimeModelRouter.ModelCapabilities(nativeSupported, cascadeSupported,
                nativeToolCallingSupported, chatOnlyRequired, toolPathRequired);
    }

    private static AiAgentConfig agent(String mode) {
        return new AiAgentConfig(101L, 11L, "xiaoyou", "system prompt",
                201L, 1, mode, 301L, 302L, 303L, 304L,
                "SESSION", false, false);
    }
}
