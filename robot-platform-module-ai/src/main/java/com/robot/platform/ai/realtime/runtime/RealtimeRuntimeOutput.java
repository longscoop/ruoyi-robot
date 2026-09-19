package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;

import java.nio.ByteBuffer;

public interface RealtimeRuntimeOutput {

    void sendEvent(RealtimeServerEvent event);

    void sendAudio(ByteBuffer audio);
}
