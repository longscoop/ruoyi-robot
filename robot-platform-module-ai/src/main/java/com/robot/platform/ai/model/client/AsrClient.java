package com.robot.platform.ai.model.client;

public interface AsrClient {

    String providerType();

    default String modelType() {
        return "ASR";
    }

    AsrSession open(ResolvedModel model, AsrListener listener);
}
