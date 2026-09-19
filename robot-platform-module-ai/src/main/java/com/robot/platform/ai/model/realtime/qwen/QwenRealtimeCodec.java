package com.robot.platform.ai.model.realtime.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QwenRealtimeCodec {

    private final AtomicLong eventSequence = new AtomicLong();

    public String encodeSessionUpdate(String instructions, String voiceConfigJson) {
        ObjectNode root = event("session.update");
        ObjectNode session = JsonUtils.getObjectMapper().createObjectNode();

        if (voiceConfigJson != null && !voiceConfigJson.isBlank()) {
            JsonNode config = JsonUtils.parseTree(voiceConfigJson);
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException("Qwen voice config must be a JSON object");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = config.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                session.set(field.getKey(), field.getValue());
            }
        }

        ArrayNode modalities = session.putArray("modalities");
        modalities.add("text");
        modalities.add("audio");
        session.put("input_audio_format", "pcm");
        session.put("output_audio_format", "pcm");
        if (instructions != null && !instructions.isBlank()) {
            session.put("instructions", instructions);
        }
        session.set("turn_detection", JsonUtils.getObjectMapper().nullNode());
        root.set("session", session);
        return JsonUtils.toJsonString(root);
    }

    public String encodeAudioAppend(ByteBuffer pcm) {
        if (pcm == null) {
            throw new IllegalArgumentException("PCM buffer must not be null");
        }
        ByteBuffer copy = pcm.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        ObjectNode root = event("input_audio_buffer.append");
        root.put("audio", Base64.getEncoder().encodeToString(bytes));
        return JsonUtils.toJsonString(root);
    }

    public String encodeSpeechStarted() {
        return JsonUtils.toJsonString(event("input_audio_buffer.clear"));
    }

    public String encodeAudioCommit() {
        return JsonUtils.toJsonString(event("input_audio_buffer.commit"));
    }

    public String encodeResponseCreate() {
        return JsonUtils.toJsonString(event("response.create"));
    }

    public String encodeResponseCancel() {
        return JsonUtils.toJsonString(event("response.cancel"));
    }

    public List<ProviderEvent> decodeServerEvent(String json) {
        final JsonNode root;
        try {
            root = JsonUtils.parseTree(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Qwen realtime JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Qwen realtime event must be a JSON object");
        }

        String type = text(root, "type");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Qwen realtime event type must not be blank");
        }

        ProviderEvent event = switch (type) {
            case "conversation.item.input_audio_transcription.delta" ->
                    new ProviderEvent.TranscriptDelta(
                            value(root, "text") + value(root, "stash"));
            case "conversation.item.input_audio_transcription.completed" ->
                    new ProviderEvent.TranscriptDone(value(root, "transcript"));
            case "response.text.delta", "response.audio_transcript.delta" ->
                    new ProviderEvent.TextDelta(value(root, "delta"));
            case "response.text.done" ->
                    new ProviderEvent.TextDone(value(root, "text"));
            case "response.audio_transcript.done" ->
                    new ProviderEvent.TextDone(value(root, "transcript"));
            case "response.audio.delta" ->
                    new ProviderEvent.AudioDelta(ByteBuffer.wrap(decodeBase64(root, "delta")));
            case "response.audio.done" -> new ProviderEvent.AudioDone();
            case "response.function_call_arguments.done" ->
                    new ProviderEvent.ToolCall(
                            value(root, "call_id"),
                            value(root, "name"),
                            value(root, "arguments"));
            case "response.done" -> decodeUsage(root);
            case "conversation.item.input_audio_transcription.failed", "error" -> decodeError(root);
            default -> null;
        };

        return event == null ? List.of() : List.of(event);
    }

    private ProviderEvent decodeUsage(JsonNode root) {
        JsonNode usage = root.path("response").path("usage");
        if (!usage.isObject()) {
            return null;
        }
        return new ProviderEvent.Usage(
                usage.has("input_tokens") ? usage.path("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.path("output_tokens").asInt() : null);
    }

    private ProviderEvent decodeError(JsonNode root) {
        JsonNode error = root.path("error");
        String errorType = value(error, "type");
        return new ProviderEvent.ProviderError(
                value(error, "code"),
                value(error, "message"),
                "server_error".equals(errorType));
    }

    private ObjectNode event(String type) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("event_id", "event_platform_" + eventSequence.incrementAndGet());
        root.put("type", type);
        return root;
    }

    private static byte[] decodeBase64(JsonNode root, String field) {
        String value = value(root, field);
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Qwen Base64 field: " + field, exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String value(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = text(node, field);
        return value == null ? "" : value;
    }
}
