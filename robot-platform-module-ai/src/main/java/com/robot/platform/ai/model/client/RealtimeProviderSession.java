package com.robot.platform.ai.model.client;

import java.nio.ByteBuffer;

public interface RealtimeProviderSession extends AutoCloseable {

    default void beginTurn(String turnId, long generation) {
    }

    void appendAudio(ByteBuffer pcm);

    void speechStarted();

    void speechStopped();

    void cancelCurrentResponse();

    @Override
    void close();
}
