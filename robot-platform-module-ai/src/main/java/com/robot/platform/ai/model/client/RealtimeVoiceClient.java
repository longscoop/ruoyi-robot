package com.robot.platform.ai.model.client;

public interface RealtimeVoiceClient {

    String providerType();

    default String modelType() {
        return "REALTIME_S2S";
    }

    RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener);
}
