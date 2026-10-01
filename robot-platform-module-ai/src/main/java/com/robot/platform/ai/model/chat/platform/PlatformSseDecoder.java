package com.robot.platform.ai.model.chat.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/** Parses complete SSE frames, so event names and data may arrive on separate lines. */
final class PlatformSseDecoder {
    private final String type;
    private final StringBuilder data = new StringBuilder();
    private String eventName;
    private boolean done;
    private boolean errored;

    PlatformSseDecoder(String type) { this.type = type; }

    List<ProviderEvent> accept(String line) {
        if (line == null) return finish();
        if (line.isEmpty()) return flush();
        if (line.startsWith(":")) return List.of();
        if (line.startsWith("event:")) eventName = line.substring(6).trim();
        else if (line.startsWith("data:")) {
            if (data.length() > 0) data.append('\n');
            data.append(line.substring(5).trim());
        }
        return List.of();
    }

    List<ProviderEvent> finish() {
        List<ProviderEvent> result = new ArrayList<>(flush());
        if (!done && !errored) result.add(new ProviderEvent.ProviderError(
                "incomplete_stream", "AI platform stream ended before completion", true));
        return result;
    }

    private List<ProviderEvent> flush() {
        String raw = data.toString();
        String event = eventName;
        data.setLength(0);
        eventName = null;
        if (raw.isBlank() || done || errored) return List.of();
        if ("[DONE]".equals(raw)) return complete();
        JsonNode root = JsonUtils.parseTree(raw);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid AI platform event");
        return switch (type) {
            case "QWEN", "FASTGPT" -> openAi(root, event);
            case "DIFY" -> dify(root, event);
            case "COZE" -> coze(root, event);
            default -> throw new IllegalStateException("Unsupported AI platform");
        };
    }

    private List<ProviderEvent> openAi(JsonNode root, String event) {
        if ("error".equals(event) || root.has("error")) return error("provider_error");
        List<ProviderEvent> result = new ArrayList<>();
        for (JsonNode choice : root.path("choices")) {
            JsonNode delta = choice.path("delta");
            String content = text(delta, "content");
            if (content != null && !content.isEmpty()) result.add(new ProviderEvent.TextDelta(content));
            for (JsonNode call : delta.path("tool_calls")) {
                JsonNode function = call.path("function");
                result.add(new ProviderEvent.ToolCallDelta(call.path("index").asInt(0),
                        text(call, "id"), text(function, "name"), text(function, "arguments")));
            }
        }
        JsonNode usage = root.path("usage");
        if (usage.isObject()) result.add(new ProviderEvent.Usage(number(usage, "prompt_tokens"), number(usage, "completion_tokens")));
        return result;
    }

    private List<ProviderEvent> dify(JsonNode root, String event) {
        String kind = event == null ? text(root, "event") : event;
        if ("error".equals(kind)) return error("provider_error");
        if ("message_end".equals(kind)) {
            JsonNode usage = root.path("metadata").path("usage");
            List<ProviderEvent> result = new ArrayList<>();
            if (usage.isObject()) result.add(new ProviderEvent.Usage(number(usage, "prompt_tokens"), number(usage, "completion_tokens")));
            result.addAll(complete());
            return result;
        }
        if ("message".equals(kind) || "agent_message".equals(kind)) {
            String answer = text(root, "answer");
            if (answer != null && !answer.isEmpty()) return List.of(new ProviderEvent.TextDelta(answer));
        }
        return List.of();
    }

    private List<ProviderEvent> coze(JsonNode root, String event) {
        if ("conversation.chat.failed".equals(event) || "conversation.chat.requires_action".equals(event)) {
            return error("provider_error");
        }
        if ("conversation.chat.completed".equals(event)) {
            JsonNode usage = root.path("usage");
            List<ProviderEvent> result = new ArrayList<>();
            if (usage.isObject()) result.add(new ProviderEvent.Usage(number(usage, "input_tokens"), number(usage, "output_tokens")));
            result.addAll(complete());
            return result;
        }
        if ("conversation.message.delta".equals(event) && "answer".equals(text(root, "type"))) {
            String content = text(root, "content");
            if (content != null && !content.isEmpty()) return List.of(new ProviderEvent.TextDelta(content));
        }
        return List.of();
    }

    private List<ProviderEvent> complete() {
        if (done || errored) return List.of();
        done = true;
        return List.of(new ProviderEvent.TextDone(""));
    }

    private List<ProviderEvent> error(String code) {
        errored = true;
        return List.of(new ProviderEvent.ProviderError(code, "AI platform reported a failed chat", false));
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static Integer number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && value.isNumber() ? value.intValue() : null;
    }
}
