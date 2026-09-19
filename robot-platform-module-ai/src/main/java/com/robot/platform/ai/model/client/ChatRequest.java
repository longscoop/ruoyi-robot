package com.robot.platform.ai.model.client;

import java.util.List;
import java.util.Objects;

public record ChatRequest(ResolvedModel model, List<ChatMessage> messages, List<ChatTool> tools) {

    public ChatRequest {
        Objects.requireNonNull(model, "model");
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public ChatRequest(ResolvedModel model, List<ChatMessage> messages) {
        this(model, messages, List.of());
    }

    public record ChatMessage(String role, String content) {
    }

    public record ChatTool(String name, String description, String parametersJson) {
    }
}
