package com.robot.platform.ai.realtime.protocol;

public record RealtimeAudioFormat(String codec, int sampleRate, int channels) {

    public RealtimeAudioFormat {
        if (codec == null || codec.isBlank()) {
            throw new IllegalArgumentException("audio codec must not be blank");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("audio sampleRate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("audio channels must be positive");
        }
        codec = codec.trim();
    }
}
