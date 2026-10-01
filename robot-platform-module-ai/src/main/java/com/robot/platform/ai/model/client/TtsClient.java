package com.robot.platform.ai.model.client;

public interface TtsClient {

    String providerType();

    default String modelType() {
        return "TTS";
    }

    TtsStream stream(TtsRequest request, TtsListener listener);

    /** Optional incremental input; null keeps existing sentence-based clients compatible. */
    default TtsSession openSession(ResolvedModel model, TtsListener listener) { return null; }
}
