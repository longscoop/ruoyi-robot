package com.robot.platform.ai.model.chat.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.ChatListener;
import com.robot.platform.ai.model.client.ChatModelClient;
import com.robot.platform.ai.model.client.ChatRequest;
import com.robot.platform.ai.model.client.ChatStream;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Provider workflows can answer and plan; emitted text and tool events never execute robot commands. */
public final class PlatformChatModelClient implements ChatModelClient {
    private static final Set<String> TYPES = Set.of("QWEN", "FASTGPT", "DIFY", "COZE");
    private final String type;
    private final HttpClient http;

    public PlatformChatModelClient(String type, HttpClient http) {
        this.type = Objects.requireNonNull(type).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(this.type)) throw new IllegalArgumentException("Unsupported AI platform: " + type);
        this.http = Objects.requireNonNull(http);
    }

    @Override
    public String providerType() { return type; }

    @Override
    public ChatStream stream(ChatRequest request, ChatListener listener) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(listener);
        ResolvedModel model = request.model();
        if (!type.equals(model.providerType()) || !"CHAT".equals(model.modelType())) {
            throw new IllegalArgumentException("CHAT model/provider mismatch for " + type);
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException(type + " credential is required");
        }
        if (!request.tools().isEmpty() && ("COZE".equals(type) || "DIFY".equals(type) || "FASTGPT".equals(type))) {
            throw new IllegalArgumentException(type + " app workflows do not accept cloud robot tool definitions");
        }
        HttpRequest outbound = HttpRequest.newBuilder(endpoint(model))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + model.credential())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body(request), StandardCharsets.UTF_8))
                .build();
        CompletableFuture<HttpResponse<InputStream>> future = http.sendAsync(outbound, HttpResponse.BodyHandlers.ofInputStream());
        PlatformStream stream = new PlatformStream(future);
        future.whenCompleteAsync((response, failure) -> {
            if (stream.cancelled.get()) {
                if (response != null) close(response.body());
                return;
            }
            if (failure != null) {
                listener.onEvent(new ProviderEvent.ProviderError("transport_error", "AI platform request failed", true));
                return;
            }
            stream.body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                listener.onEvent(new ProviderEvent.ProviderError("http_" + response.statusCode(),
                        "AI platform request failed", response.statusCode() >= 500));
                close(response.body());
                return;
            }
            PlatformSseDecoder decoder = new PlatformSseDecoder(type);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while (!stream.cancelled.get() && (line = reader.readLine()) != null) {
                    for (ProviderEvent event : decoder.accept(line)) {
                        if (!stream.cancelled.get()) listener.onEvent(event);
                    }
                }
                if (!stream.cancelled.get()) for (ProviderEvent event : decoder.finish()) listener.onEvent(event);
            } catch (IOException | RuntimeException error) {
                if (!stream.cancelled.get()) listener.onEvent(new ProviderEvent.ProviderError(
                        "stream_error", "AI platform stream failed", true));
            } finally {
                stream.body = null;
            }
        });
        return stream;
    }

    URI endpoint(ResolvedModel model) {
        String base = model.baseUrl().replaceAll("/+$", "");
        String suffix = switch (type) {
            case "QWEN", "FASTGPT" -> "/chat/completions";
            case "DIFY" -> "/chat-messages";
            case "COZE" -> "/v3/chat";
            default -> throw new IllegalStateException();
        };
        if (!base.endsWith(suffix)) base += suffix;
        URI uri = URI.create(base);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("AI platform baseUrl must be an HTTP(S) endpoint");
        }
        return uri;
    }

    String body(ChatRequest request) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        switch (type) {
            case "QWEN", "FASTGPT" -> {
                if ("QWEN".equals(type)) root.put("model", request.model().modelCode());
                else root.put("appId", request.model().modelCode());
                root.put("stream", true);
                ArrayNode messages = root.putArray("messages");
                for (ChatRequest.ChatMessage message : request.messages()) {
                    ObjectNode item = messages.addObject();
                    item.put("role", message.role());
                    item.put("content", message.content());
                }
                if ("QWEN".equals(type) && !request.tools().isEmpty()) {
                    ArrayNode tools = root.putArray("tools");
                    for (ChatRequest.ChatTool tool : request.tools()) {
                        ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                        function.put("name", tool.name());
                        if (tool.description() != null) function.put("description", tool.description());
                        if (tool.parametersJson() != null) function.set("parameters", JsonUtils.parseTree(tool.parametersJson()));
                    }
                }
            }
            case "DIFY" -> {
                root.set("inputs", config(request.model()));
                root.put("query", transcript(request));
                root.put("response_mode", "streaming");
                root.put("user", "robot-cloud-" + UUID.randomUUID());
            }
            case "COZE" -> {
                root.put("bot_id", request.model().modelCode());
                root.put("user_id", "robot-cloud-" + UUID.randomUUID());
                root.put("stream", true);
                root.put("auto_save_history", false);
                root.putArray("additional_messages").addObject()
                        .put("role", "user").put("content_type", "text").put("content", transcript(request));
            }
            default -> throw new IllegalStateException();
        }
        return JsonUtils.toJsonString(root);
    }

    private static ObjectNode config(ResolvedModel model) {
        ObjectNode empty = JsonUtils.getObjectMapper().createObjectNode();
        if (model.modelConfigJson() == null || model.modelConfigJson().isBlank()) return empty;
        JsonNode config = JsonUtils.parseTree(model.modelConfigJson());
        JsonNode inputs = config.path("inputs");
        if (!inputs.isObject()) return empty;
        return (ObjectNode) inputs.deepCopy();
    }

    private static String transcript(ChatRequest request) {
        StringBuilder text = new StringBuilder();
        for (ChatRequest.ChatMessage message : request.messages()) {
            if (message.content() == null || message.content().isBlank()) continue;
            text.append(message.role()).append(": ").append(message.content()).append('\n');
        }
        if (text.length() == 0) throw new IllegalArgumentException("Chat request needs a message");
        return text.toString().trim();
    }

    private static void close(InputStream body) {
        if (body != null) try { body.close(); } catch (IOException ignored) { }
    }

    private static final class PlatformStream implements ChatStream {
        private final CompletableFuture<HttpResponse<InputStream>> future;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile InputStream body;

        private PlatformStream(CompletableFuture<HttpResponse<InputStream>> future) { this.future = future; }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                future.cancel(true);
                PlatformChatModelClient.close(body);
            }
        }
    }
}
