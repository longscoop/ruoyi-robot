package com.robot.platform.ai.memory.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class LlmMemoryExtractor implements MemoryExtractor {
    public static final String PROMPT_CODE = "MEMORY_EXTRACT";

    private final ChatModelClient chatClient;
    private final ResolvedModel model;
    private final String prompt;
    private final ObjectMapper objectMapper;
    private final ExtractionErrorRecorder errorRecorder;

    public LlmMemoryExtractor(ChatModelClient chatClient, ResolvedModel model, String prompt,
                              ObjectMapper objectMapper, ExtractionErrorRecorder errorRecorder) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.model = Objects.requireNonNull(model, "model");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.errorRecorder = errorRecorder == null ? cause -> { } : errorRecorder;
    }

    @Override
    public List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
                                         MemoryDirectiveParser.Directive directive) {
        if (turn == null || directive == MemoryDirectiveParser.Directive.DO_NOT_REMEMBER
                || directive == MemoryDirectiveParser.Directive.FORGET) return List.of();

        StringBuilder json = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean();
        ChatRequest request = new ChatRequest(model, List.of(
                new ChatRequest.ChatMessage("system", prompt),
                new ChatRequest.ChatMessage("user", "USER: " + safe(turn.userText())
                        + "\nASSISTANT: " + safe(turn.assistantText()))));
        try {
            chatClient.stream(request, event -> {
                if (event instanceof ProviderEvent.TextDelta delta) json.append(delta.text());
                else if (event instanceof ProviderEvent.TextDone done) {
                    if (json.isEmpty()) json.append(done.text());
                } else if (event instanceof ProviderEvent.ProviderError) failed.set(true);
            });
            if (failed.get() || json.isEmpty()) return List.of();
            return parseStrict(json.toString());
        } catch (RuntimeException exception) {
            errorRecorder.record(exception);
            return List.of();
        }
    }

    private List<MemoryCandidate> parseStrict(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray()) throw new IllegalArgumentException("candidates array required");
            List<MemoryCandidate> result = new ArrayList<>();
            for (JsonNode node : candidates) {
                requireOnly(node, "scope", "memoryType", "content", "importance", "confidence", "expiresAt");
                result.add(new MemoryCandidate(
                        requiredText(node, "scope"), requiredText(node, "memoryType"), requiredText(node, "content"),
                        requiredProbability(node, "importance"), requiredProbability(node, "confidence"),
                        node.hasNonNull("expiresAt") ? LocalDateTime.parse(node.get("expiresAt").asText()) : null));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            errorRecorder.record(exception);
            return List.of();
        }
    }

    private static void requireOnly(JsonNode node, String... fields) {
        if (!node.isObject()) throw new IllegalArgumentException("candidate must be object");
        java.util.Set<String> allowed = java.util.Set.of(fields);
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("unexpected field: " + name);
        });
    }

    private static String requiredText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(name + " required");
        return value.asText();
    }

    private static double requiredProbability(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(name + " required");
        double number = value.asDouble();
        if (number < 0 || number > 1) throw new IllegalArgumentException(name + " out of range");
        return number;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    @FunctionalInterface
    public interface ExtractionErrorRecorder { void record(Throwable cause); }
}
