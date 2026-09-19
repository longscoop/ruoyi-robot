package com.robot.platform.ai.model.realtime.doubao;

import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoRealtimeCodecTest {

    private final DoubaoRealtimeCodec codec = new DoubaoRealtimeCodec();

    @Test
    void encodesExactBinaryHeadersAndLifecycleEvents() {
        byte[] startConnection = bytes(codec.encodeStartConnection());
        assertArrayEquals(new byte[]{0x11, 0x14, 0x11, 0x00},
                java.util.Arrays.copyOfRange(startConnection, 0, 4));
        assertEquals(1, int32(startConnection, 4));

        var config = new DoubaoRealtimeCodec.SessionConfig(
                "小优", "你是家庭机器人小优。", "简洁自然",
                "zh_female_vv_jupiter_bigtts", 24000, 120, "audio", "1.2.1.1");
        byte[] startSession = bytes(codec.encodeStartSession("sess-1", config));
        assertArrayEquals(new byte[]{0x11, 0x14, 0x11, 0x00},
                java.util.Arrays.copyOfRange(startSession, 0, 4));
        assertEquals(100, int32(startSession, 4));
        assertEquals("sess-1", sessionId(startSession, 8));

        String payload = unzipJsonPayload(startSession, 8 + 4 + "sess-1".getBytes(StandardCharsets.UTF_8).length);
        assertEquals(JsonUtils.parseTree(resource("/ai/doubao/start-session.json")), JsonUtils.parseTree(payload));

        byte[] audio = bytes(codec.encodeAudio("sess-1", ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
        assertArrayEquals(new byte[]{0x11, 0x24, 0x01, 0x00},
                java.util.Arrays.copyOfRange(audio, 0, 4));
        assertEquals(200, int32(audio, 4));
        assertEquals("sess-1", sessionId(audio, 8));

        assertEquals(400, int32(bytes(codec.encodeEndAsr("sess-1")), 4));
        assertEquals(515, int32(bytes(codec.encodeClientInterrupt("sess-1")), 4));
        assertEquals(102, int32(bytes(codec.encodeFinishSession("sess-1")), 4));
        assertEquals(2, int32(bytes(codec.encodeFinishConnection()), 4));
    }

    @Test
    void decodesAndNormalizesSanitizedServerFixtures() {
        assertOne(codec.decodeServerFrame(serverJson(451, "sess-1", resource("/ai/doubao/asr-response.json"))),
                ProviderEvent.TranscriptDone.class, event ->
                        assertEquals("你好小优", ((ProviderEvent.TranscriptDone) event).text()));

        assertOne(codec.decodeServerFrame(serverJson(550, "sess-1", resource("/ai/doubao/chat-response.json"))),
                ProviderEvent.TextDelta.class, event ->
                        assertEquals("我在这里", ((ProviderEvent.TextDelta) event).text()));

        assertOne(codec.decodeServerFrame(serverBinary(352, "sess-1", new byte[]{9, 8, 7})),
                ProviderEvent.AudioDelta.class, event -> {
                    ByteBuffer audio = ((ProviderEvent.AudioDelta) event).audio();
                    byte[] actual = new byte[audio.remaining()];
                    audio.get(actual);
                    assertArrayEquals(new byte[]{9, 8, 7}, actual);
                });

        assertOne(codec.decodeServerFrame(serverJson(359, "sess-1", "{}")),
                ProviderEvent.AudioDone.class, event -> {});

        assertOne(codec.decodeServerFrame(serverJson(154, "sess-1", resource("/ai/doubao/usage-response.json"))),
                ProviderEvent.Usage.class, event -> {
                    ProviderEvent.Usage usage = (ProviderEvent.Usage) event;
                    assertEquals(12, usage.inputTokens());
                    assertEquals(7, usage.outputTokens());
                });

        List<ProviderEvent> ended = codec.decodeServerFrame(serverJson(559, "sess-1", "{}"));
        assertOne(ended, ProviderEvent.TextDone.class,
                event -> assertEquals("", ((ProviderEvent.TextDone) event).text()));
    }

    @Test
    void distinguishesInterimAndFinalAsrResults() {
        assertOne(codec.decodeServerFrame(serverJson(
                        451, "sess-1", "{\"results\":[{\"text\":\"你\",\"is_interim\":true}]}")),
                ProviderEvent.TranscriptDelta.class,
                event -> assertEquals("你", ((ProviderEvent.TranscriptDelta) event).text()));

        assertOne(codec.decodeServerFrame(serverJson(
                        451, "sess-1", "{\"results\":[{\"text\":\"你好\",\"is_interim\":false}]}")),
                ProviderEvent.TranscriptDone.class,
                event -> assertEquals("你好", ((ProviderEvent.TranscriptDone) event).text()));
    }

    @Test
    void normalizesProtocolErrorsWithoutLeakingRawFrames() {
        byte[] error = serverError(50000001, "upstream unavailable");

        List<ProviderEvent> events = codec.decodeServerFrame(ByteBuffer.wrap(error));

        assertOne(events, ProviderEvent.ProviderError.class, event -> {
            ProviderEvent.ProviderError value = (ProviderEvent.ProviderError) event;
            assertEquals("50000001", value.code());
            assertEquals("upstream unavailable", value.message());
            assertTrue(value.retryable());
        });
    }

    @Test
    void ignoresConnectionAndSessionAckEvents() {
        assertTrue(codec.decodeServerFrame(serverJson(50, "conn-1", "{}")).isEmpty());
        assertTrue(codec.decodeServerFrame(serverJson(150, "sess-1", "{}")).isEmpty());
    }

    private static ByteBuffer serverJson(int event, String sessionId, String json) {
        return serverFrame(event, sessionId, gzip(json.getBytes(StandardCharsets.UTF_8)), true, true);
    }

    private static ByteBuffer serverBinary(int event, String sessionId, byte[] payload) {
        return serverFrame(event, sessionId, gzip(payload), false, true);
    }

    private static ByteBuffer serverFrame(int event, String sessionId, byte[] payload,
                                          boolean json, boolean gzip) {
        byte[] sid = sessionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + sid.length + 4 + payload.length);
        buffer.put((byte) 0x11);
        buffer.put((byte) 0x94);
        buffer.put((byte) ((json ? 0x10 : 0x00) | (gzip ? 0x01 : 0x00)));
        buffer.put((byte) 0x00);
        buffer.putInt(event);
        buffer.putInt(sid.length).put(sid);
        buffer.putInt(payload.length).put(payload);
        buffer.flip();
        return buffer;
    }

    private static byte[] serverError(int code, String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + payload.length);
        buffer.put((byte) 0x11).put((byte) 0xF0).put((byte) 0x00).put((byte) 0x00);
        buffer.putInt(code).putInt(payload.length).put(payload);
        return buffer.array();
    }

    private static void assertOne(List<ProviderEvent> events,
                                  Class<? extends ProviderEvent> type,
                                  java.util.function.Consumer<ProviderEvent> assertion) {
        assertEquals(1, events.size());
        assertInstanceOf(type, events.get(0));
        assertion.accept(events.get(0));
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static int int32(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).getInt();
    }

    private static String sessionId(byte[] bytes, int offset) {
        int length = int32(bytes, offset);
        return new String(bytes, offset + 4, length, StandardCharsets.UTF_8);
    }

    private static String unzipJsonPayload(byte[] frame, int offset) {
        int length = int32(frame, offset);
        byte[] compressed = java.util.Arrays.copyOfRange(frame, offset + 4, offset + 4 + length);
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
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

    private static String resource(String path) {
        try (InputStream in = DoubaoRealtimeCodecTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
