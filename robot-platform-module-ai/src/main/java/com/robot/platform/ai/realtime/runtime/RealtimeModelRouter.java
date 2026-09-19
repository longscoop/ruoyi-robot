package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RealtimeModelRouter {

    public RealtimeRoute route(AiAgentConfig agent, ModelCapabilities capabilities) {
        if (agent == null) {
            throw new IllegalArgumentException("agent config must not be null");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("model capabilities must not be null");
        }
        if (agent.realtimeMode() == null || agent.realtimeMode().isBlank()) {
            throw new IllegalStateException("Agent realtime mode is missing");
        }

        return switch (agent.realtimeMode().trim().toUpperCase(Locale.ROOT)) {
            case "NATIVE" -> nativeRoute(agent, capabilities, "AGENT_NATIVE");
            case "CASCADE" -> cascadeRoute(agent, capabilities, "AGENT_CASCADE");
            case "AUTO" -> autoRoute(agent, capabilities);
            default -> throw new IllegalStateException("Unsupported realtime mode: " + agent.realtimeMode());
        };
    }

    private static RealtimeRoute autoRoute(AiAgentConfig agent, ModelCapabilities capabilities) {
        boolean capabilityFallback = !capabilities.nativeRealtimeSupported()
                || capabilities.turnRequiresChatOnlyCapability()
                || (capabilities.turnRequiresToolPath() && !capabilities.nativeToolCallingSupported());
        if (capabilityFallback) {
            return cascadeRoute(agent, capabilities, "AUTO_CAPABILITY_FALLBACK");
        }
        return nativeRoute(agent, capabilities, "AUTO_NATIVE");
    }

    private static RealtimeRoute nativeRoute(AiAgentConfig agent, ModelCapabilities capabilities, String reason) {
        if (!capabilities.nativeRealtimeSupported() || agent.realtimeModelId() == null) {
            throw new IllegalStateException("Native realtime route is unavailable");
        }
        return new RealtimeRoute(RealtimeRoute.Mode.NATIVE, reason,
                agent.realtimeModelId(), null, null, null);
    }

    private static RealtimeRoute cascadeRoute(AiAgentConfig agent, ModelCapabilities capabilities, String reason) {
        if (!capabilities.cascadeSupported()
                || agent.conversationModelId() == null
                || agent.asrModelId() == null
                || agent.ttsModelId() == null) {
            throw new IllegalStateException("Cascade realtime route is unavailable");
        }
        return new RealtimeRoute(RealtimeRoute.Mode.CASCADE, reason,
                null, agent.conversationModelId(), agent.asrModelId(), agent.ttsModelId());
    }

    public record ModelCapabilities(boolean nativeRealtimeSupported,
                                    boolean cascadeSupported,
                                    boolean nativeToolCallingSupported,
                                    boolean turnRequiresChatOnlyCapability,
                                    boolean turnRequiresToolPath) {

        public static ModelCapabilities configured(AiAgentConfig agent) {
            boolean nativeConfigured = agent != null && agent.realtimeModelId() != null;
            boolean cascadeConfigured = agent != null
                    && agent.conversationModelId() != null
                    && agent.asrModelId() != null
                    && agent.ttsModelId() != null;
            return new ModelCapabilities(nativeConfigured, cascadeConfigured,
                    false, false, false);
        }

        public ModelCapabilities withTurnRequirements(boolean chatOnlyCapability, boolean toolPath) {
            return new ModelCapabilities(nativeRealtimeSupported, cascadeSupported,
                    nativeToolCallingSupported, chatOnlyCapability, toolPath);
        }
    }
}
