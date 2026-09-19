package com.robot.platform.ai.realtime.runtime;

public record RealtimeRoute(Mode mode, String routeReason,
                            Long realtimeModelId, Long conversationModelId,
                            Long asrModelId, Long ttsModelId) {

    public enum Mode {
        NATIVE,
        CASCADE
    }

    public RealtimeRoute {
        if (mode == null) {
            throw new IllegalArgumentException("route mode must not be null");
        }
        if (routeReason == null || routeReason.isBlank()) {
            throw new IllegalArgumentException("routeReason must not be blank");
        }
    }
}
