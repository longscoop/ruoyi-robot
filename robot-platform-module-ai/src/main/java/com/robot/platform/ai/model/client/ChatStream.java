package com.robot.platform.ai.model.client;

public interface ChatStream extends AutoCloseable {

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
