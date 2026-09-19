package com.robot.platform.ai.model.realtime.doubao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

final class DoubaoRealtimeCodecTestHelper {
    private DoubaoRealtimeCodecTestHelper() {}

    static ByteBuffer serverFrame(int event, String sessionId, byte[] rawPayload, boolean json) {
        byte[] payload = gzip(rawPayload);
        byte[] sid = sessionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + sid.length + 4 + payload.length);
        buffer.put((byte) 0x11).put((byte) 0x94)
                .put((byte) ((json ? 0x10 : 0x00) | 0x01)).put((byte) 0x00);
        buffer.putInt(event);
        buffer.putInt(sid.length).put(sid);
        buffer.putInt(payload.length).put(payload);
        buffer.flip();
        return buffer;
    }

    private static byte[] gzip(byte[] payload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(payload);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
