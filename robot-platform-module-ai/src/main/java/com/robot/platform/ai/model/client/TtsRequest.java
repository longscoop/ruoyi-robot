package com.robot.platform.ai.model.client;

import java.util.Objects;

public record TtsRequest(ResolvedModel model, String text) {

    public TtsRequest {
        Objects.requireNonNull(model, "model");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS text must not be blank");
        }
    }
}
