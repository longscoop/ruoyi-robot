package com.robot.platform.ai.model.client;

public interface TtsStream extends AutoCloseable {

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
