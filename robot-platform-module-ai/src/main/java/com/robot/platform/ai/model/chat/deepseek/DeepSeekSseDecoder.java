package com.robot.platform.ai.model.chat.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeepSeekSseDecoder {

    public List<ProviderEvent> decodeLine(String line) {
        if (line == null || line.isBlank() || line.startsWith(":") || !line.startsWith("data:")) {
            return List.of();
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty()) {
            return List.of();
        }
        if ("[DONE]".equals(data)) {
            return List.of(new ProviderEvent.TextDone(""));
        }

        final JsonNode root;
        try {
            root = JsonUtils.parseTree(data);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid DeepSeek SSE data JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("DeepSeek SSE data must be a JSON object");
        }

        List<ProviderEvent> events = new ArrayList<>();
        JsonNode choices = root.path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                JsonNode delta = choice.path("delta");
                if (delta.isObject()) {
                    JsonNode content = delta.get("content");
                    if (content != null && content.isTextual() && !content.textValue().isEmpty()) {
                        events.add(new ProviderEvent.TextDelta(content.textValue()));
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode toolCall : toolCalls) {
                            JsonNode function = toolCall.path("function");
                            events.add(new ProviderEvent.ToolCallDelta(
                                    toolCall.path("index").asInt(0),
                                    nullableText(toolCall, "id"),
                                    nullableText(function, "name"),
                                    nullableText(function, "arguments")));
                        }
                    }
                }
            }
        }

        JsonNode usage = root.path("usage");
        if (usage.isObject() && !usage.isNull()) {
            events.add(new ProviderEvent.Usage(
                    usage.has("prompt_tokens") ? usage.path("prompt_tokens").asInt() : null,
                    usage.has("completion_tokens") ? usage.path("completion_tokens").asInt() : null));
        }

        return List.copyOf(events);
    }

    private static String nullableText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
