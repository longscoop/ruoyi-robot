package com.robot.platform.ai.model.realtime.doubao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class DoubaoRealtimeCodec {

    static final int EVENT_START_CONNECTION = 1;
    static final int EVENT_FINISH_CONNECTION = 2;
    static final int EVENT_START_SESSION = 100;
    static final int EVENT_FINISH_SESSION = 102;
    static final int EVENT_AUDIO = 200;
    static final int EVENT_END_ASR = 400;
    static final int EVENT_CLIENT_INTERRUPT = 515;

    static final int EVENT_CONNECTION_STARTED = 50;
    static final int EVENT_SESSION_STARTED = 150;
    static final int EVENT_USAGE = 154;
    static final int EVENT_ASR_INFO = 450;
    static final int EVENT_ASR_RESPONSE = 451;
    static final int EVENT_ASR_ENDED = 459;
    static final int EVENT_TTS_SENTENCE_START = 350;
    static final int EVENT_TTS_SENTENCE_END = 351;
    static final int EVENT_TTS_RESPONSE = 352;
    static final int EVENT_TTS_ENDED = 359;
    static final int EVENT_CHAT_RESPONSE = 550;
    static final int EVENT_CHAT_ENDED = 559;

    private static final int CLIENT_FULL_REQUEST = 0x1;
    private static final int CLIENT_AUDIO_REQUEST = 0x2;
    private static final int SERVER_FULL_RESPONSE = 0x9;
    private static final int SERVER_ACK = 0xB;
    private static final int SERVER_ERROR = 0xF;
    private static final int FLAG_WITH_EVENT = 0x4;
    private static final int SERIALIZATION_NONE = 0x0;
    private static final int SERIALIZATION_JSON = 0x1;
    private static final int COMPRESSION_NONE = 0x0;
    private static final int COMPRESSION_GZIP = 0x1;

    public ByteBuffer encodeStartConnection() {
        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_START_CONNECTION,
                null, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteBuffer encodeStartSession(String sessionId, SessionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Doubao session config must not be null");
        }
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();

        ObjectNode asr = root.putObject("asr");
        asr.putObject("extra").put("end_smooth_window_ms", 1500);

        ObjectNode tts = root.putObject("tts");
        tts.put("speaker", requireText(config.speaker(), "speaker"));
        ObjectNode audio = tts.putObject("audio_config");
        audio.put("channel", 1);
        audio.put("format", "pcm_s16le");
        audio.put("sample_rate", requirePositive(config.outputSampleRate(), "outputSampleRate"));

        ObjectNode dialog = root.putObject("dialog");
        dialog.put("bot_name", requireText(config.botName(), "botName"));
        dialog.put("system_role", config.systemRole() == null ? "" : config.systemRole());
        dialog.put("speaking_style", config.speakingStyle() == null ? "" : config.speakingStyle());
        ObjectNode extra = dialog.putObject("extra");
        extra.put("strict_audit", false);
        extra.put("recv_timeout", requirePositive(config.recvTimeout(), "recvTimeout"));
        extra.put("input_mod", requireText(config.inputMod(), "inputMod"));
        if (config.model() != null && !config.model().isBlank()) {
            extra.put("model", config.model().trim());
        }

        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_START_SESSION,
                requireText(sessionId, "sessionId"), JsonUtils.toJsonString(root).getBytes(StandardCharsets.UTF_8));
    }

    public ByteBuffer encodeAudio(String sessionId, ByteBuffer pcm) {
        if (pcm == null) {
            throw new IllegalArgumentException("PCM buffer must not be null");
        }
        ByteBuffer copy = pcm.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return encodeClientFrame(CLIENT_AUDIO_REQUEST, SERIALIZATION_NONE, EVENT_AUDIO,
                requireText(sessionId, "sessionId"), bytes);
    }

    public ByteBuffer encodeEndAsr(String sessionId) {
        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_END_ASR,
                requireText(sessionId, "sessionId"), "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteBuffer encodeClientInterrupt(String sessionId) {
        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_CLIENT_INTERRUPT,
                requireText(sessionId, "sessionId"), "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteBuffer encodeFinishSession(String sessionId) {
        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_FINISH_SESSION,
                requireText(sessionId, "sessionId"), "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteBuffer encodeFinishConnection() {
        return encodeClientFrame(CLIENT_FULL_REQUEST, SERIALIZATION_JSON, EVENT_FINISH_CONNECTION,
                null, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public List<ProviderEvent> decodeServerFrame(ByteBuffer frame) {
        return normalize(decodeFrame(frame));
    }

    List<ProviderEvent> normalize(DecodedFrame decoded) {
        if (decoded.errorCode() != null) {
            return List.of(new ProviderEvent.ProviderError(
                    Integer.toString(decoded.errorCode()),
                    new String(decoded.payload(), StandardCharsets.UTF_8),
                    decoded.errorCode() >= 50_000_000));
        }

        ProviderEvent event = switch (decoded.event()) {
            case EVENT_ASR_RESPONSE -> asrEvent(decoded.json());
            case EVENT_CHAT_RESPONSE -> new ProviderEvent.TextDelta(text(decoded.json(), "content"));
            case EVENT_CHAT_ENDED -> new ProviderEvent.TextDone("");
            case EVENT_TTS_RESPONSE -> new ProviderEvent.AudioDelta(ByteBuffer.wrap(decoded.payload()));
            case EVENT_TTS_ENDED -> new ProviderEvent.AudioDone();
            case EVENT_USAGE -> new ProviderEvent.Usage(
                    intOrNull(decoded.json().path("usage"), "input_tokens"),
                    intOrNull(decoded.json().path("usage"), "output_tokens"));
            default -> null;
        };
        return event == null ? List.of() : List.of(event);
    }

    DecodedFrame decodeFrame(ByteBuffer source) {
        if (source == null) {
            throw new IllegalArgumentException("Doubao server frame must not be null");
        }
        ByteBuffer frame = source.duplicate();
        if (frame.remaining() < 4) {
            throw new IllegalArgumentException("Doubao server frame is shorter than protocol header");
        }

        int first = Byte.toUnsignedInt(frame.get());
        int version = first >>> 4;
        int headerWords = first & 0x0F;
        if (version != 1 || headerWords < 1) {
            throw new IllegalArgumentException("Unsupported Doubao protocol header");
        }

        int second = Byte.toUnsignedInt(frame.get());
        int messageType = second >>> 4;
        int flags = second & 0x0F;
        int third = Byte.toUnsignedInt(frame.get());
        int serialization = third >>> 4;
        int compression = third & 0x0F;
        frame.get();

        int headerBytes = headerWords * 4;
        int extensionBytes = headerBytes - 4;
        if (extensionBytes > frame.remaining()) {
            throw new IllegalArgumentException("Invalid Doubao extended header size");
        }
        frame.position(frame.position() + extensionBytes);

        if (messageType == SERVER_ERROR) {
            requireRemaining(frame, 8);
            int code = frame.getInt();
            byte[] payload = readPayload(frame);
            return new DecodedFrame(messageType, 0, null, payload, null, code);
        }
        if (messageType != SERVER_FULL_RESPONSE && messageType != SERVER_ACK) {
            throw new IllegalArgumentException("Unsupported Doubao server message type: " + messageType);
        }

        int event = 0;
        if ((flags & FLAG_WITH_EVENT) != 0) {
            requireRemaining(frame, 4);
            event = frame.getInt();
        }

        requireRemaining(frame, 4);
        int sessionLength = frame.getInt();
        if (sessionLength < 0 || sessionLength > frame.remaining()) {
            throw new IllegalArgumentException("Invalid Doubao session id length");
        }
        byte[] sessionBytes = new byte[sessionLength];
        frame.get(sessionBytes);
        String sessionId = new String(sessionBytes, StandardCharsets.UTF_8);

        byte[] compressedPayload = readPayload(frame);
        byte[] payload = compression == COMPRESSION_GZIP
                ? gunzip(compressedPayload)
                : compressedPayload;

        JsonNode json = null;
        if (serialization == SERIALIZATION_JSON && payload.length > 0) {
            json = JsonUtils.parseTree(new String(payload, StandardCharsets.UTF_8));
        } else if (serialization != SERIALIZATION_NONE) {
            throw new IllegalArgumentException("Unsupported Doubao serialization: " + serialization);
        }

        return new DecodedFrame(messageType, event, sessionId, payload, json, null);
    }

    private static ByteBuffer encodeClientFrame(int messageType, int serialization, int event,
                                                String sessionId, byte[] rawPayload) {
        byte[] payload = gzip(rawPayload);
        byte[] sessionBytes = sessionId == null ? null : sessionId.getBytes(StandardCharsets.UTF_8);
        int size = 4 + 4 + (sessionBytes == null ? 0 : 4 + sessionBytes.length) + 4 + payload.length;
        ByteBuffer frame = ByteBuffer.allocate(size);
        frame.put((byte) 0x11);
        frame.put((byte) ((messageType << 4) | FLAG_WITH_EVENT));
        frame.put((byte) ((serialization << 4) | COMPRESSION_GZIP));
        frame.put((byte) 0x00);
        frame.putInt(event);
        if (sessionBytes != null) {
            frame.putInt(sessionBytes.length).put(sessionBytes);
        }
        frame.putInt(payload.length).put(payload);
        frame.flip();
        return frame;
    }

    private static byte[] readPayload(ByteBuffer frame) {
        requireRemaining(frame, 4);
        int length = frame.getInt();
        if (length < 0 || length > frame.remaining()) {
            throw new IllegalArgumentException("Invalid Doubao payload length");
        }
        byte[] payload = new byte[length];
        frame.get(payload);
        return payload;
    }

    private static byte[] gzip(byte[] payload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(payload);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to gzip Doubao payload", exception);
        }
    }

    private static byte[] gunzip(byte[] payload) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            return gzip.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid gzip-compressed Doubao payload", exception);
        }
    }

    private static ProviderEvent asrEvent(JsonNode json) {
        if (json == null || !json.path("results").isArray() || json.path("results").isEmpty()) {
            return new ProviderEvent.TranscriptDone("");
        }
        JsonNode result = json.path("results").get(0);
        String value = result.path("text").asText("");
        return result.path("is_interim").asBoolean(false)
                ? new ProviderEvent.TranscriptDelta(value)
                : new ProviderEvent.TranscriptDone(value);
    }

    private static String text(JsonNode json, String field) {
        return json == null ? "" : json.path(field).asText("");
    }

    private static Integer intOrNull(JsonNode json, String field) {
        return json != null && json.has(field) && json.path(field).isNumber()
                ? json.path(field).asInt()
                : null;
    }

    private static void requireRemaining(ByteBuffer frame, int bytes) {
        if (frame.remaining() < bytes) {
            throw new IllegalArgumentException("Truncated Doubao server frame");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    public record SessionConfig(String botName, String systemRole, String speakingStyle,
                                String speaker, int outputSampleRate, int recvTimeout,
                                String inputMod, String model) {
    }

    record DecodedFrame(int messageType, int event, String sessionId,
                        byte[] payload, JsonNode json, Integer errorCode) {
    }
}
