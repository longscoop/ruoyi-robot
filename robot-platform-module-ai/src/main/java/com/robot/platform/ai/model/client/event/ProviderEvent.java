package com.robot.platform.ai.model.client.event;

import java.nio.ByteBuffer;
import java.util.Objects;

public sealed interface ProviderEvent permits ProviderEvent.TranscriptDelta, ProviderEvent.TranscriptDone,
        ProviderEvent.TextDelta, ProviderEvent.TextDone, ProviderEvent.AudioDelta, ProviderEvent.AudioDone,
        ProviderEvent.ToolCall, ProviderEvent.ToolCallDelta, ProviderEvent.Usage, ProviderEvent.ProviderError {

    record TranscriptDelta(String text) implements ProviderEvent {
    }

    record TranscriptDone(String text) implements ProviderEvent {
    }

    record TextDelta(String text) implements ProviderEvent {
    }

    record TextDone(String text) implements ProviderEvent {
    }

    record AudioDelta(ByteBuffer audio) implements ProviderEvent {
        public AudioDelta {
            Objects.requireNonNull(audio, "audio");
            audio = audio.asReadOnlyBuffer();
        }

        @Override
        public ByteBuffer audio() {
            return audio.asReadOnlyBuffer();
        }
    }

    record AudioDone() implements ProviderEvent {
    }

    record ToolCall(String id, String name, String argumentsJson) implements ProviderEvent {
    }

    record ToolCallDelta(int index, String id, String name, String argumentsDelta) implements ProviderEvent {
    }

    record Usage(Integer inputTokens, Integer outputTokens) implements ProviderEvent {
    }

    record ProviderError(String code, String message, boolean retryable) implements ProviderEvent {
    }
}
