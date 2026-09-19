package com.robot.platform.ai.model.client;

public interface TtsClient {

    String providerType();

    default String modelType() {
        return "TTS";
    }

    TtsStream stream(TtsRequest request, TtsListener listener);
}
