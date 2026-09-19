package com.robot.platform.ai.model.client;

import java.nio.ByteBuffer;

public interface AsrSession extends AutoCloseable {

    void appendAudio(ByteBuffer pcm);

    void speechStarted();

    void speechStopped();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
