package com.robot.platform.ai.model.chat.deepseek;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.ChatListener;
import com.robot.platform.ai.model.client.ChatModelClient;
import com.robot.platform.ai.model.client.ChatRequest;
import com.robot.platform.ai.model.client.ChatStream;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DeepSeekChatModelClient implements ChatModelClient {

    private final DeepSeekSseDecoder decoder;
    private final Transport transport;
    private final Executor executor;

    public DeepSeekChatModelClient(DeepSeekSseDecoder decoder) {
        this(decoder, new JdkTransport(HttpClient.newHttpClient()), ForkJoinPool.commonPool());
    }

    DeepSeekChatModelClient(DeepSeekSseDecoder decoder, Transport transport, Executor executor) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public String providerType() {
        return "DEEPSEEK";
    }

    @Override
    public ChatStream stream(ChatRequest request, ChatListener listener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(listener, "listener");
        ResolvedModel model = request.model();
        validate(model);

        URI uri = chatCompletionsUri(model.baseUrl());
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + model.credential(),
                "Content-Type", "application/json",
                "Accept", "text/event-stream");
        String body = requestBody(request);
        TransportCall call = transport.post(uri, headers, body);
        DeepSeekChatStream stream = new DeepSeekChatStream(call);

        call.future().whenCompleteAsync((response, failure) -> {
            if (stream.cancelled()) {
                return;
            }
            if (failure != null) {
                listener.onEvent(new ProviderEvent.ProviderError(
                        "transport_error", message(failure), true));
                return;
            }
            stream.bind(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                listener.onEvent(new ProviderEvent.ProviderError(
                        "http_" + response.statusCode(), "DeepSeek chat request failed", response.statusCode() >= 500));
                stream.closeBody();
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while (!stream.cancelled() && (line = reader.readLine()) != null) {
                    for (ProviderEvent event : decoder.decodeLine(line)) {
                        if (!stream.cancelled()) {
                            listener.onEvent(event);
                        }
                    }
                }
            } catch (IOException exception) {
                if (!stream.cancelled()) {
                    listener.onEvent(new ProviderEvent.ProviderError(
                            "stream_read_error", message(exception), true));
                }
            } finally {
                stream.clearBody();
            }
        }, executor);
        return stream;
    }

    private static void validate(ResolvedModel model) {
        if (!"DEEPSEEK".equals(model.providerType())) {
            throw new IllegalArgumentException("DeepSeek chat client requires providerType=DEEPSEEK");
        }
        if (!"CHAT".equals(model.modelType())) {
            throw new IllegalArgumentException("DeepSeek chat client requires modelType=CHAT");
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException("DeepSeek provider credential must not be blank");
        }
    }

    private static URI chatCompletionsUri(String baseUrl) {
        String value = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!value.endsWith("/chat/completions")) {
            value += "/chat/completions";
        }
        return URI.create(value);
    }

    private static String requestBody(ChatRequest request) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("model", request.model().modelCode());
        root.put("stream", true);
        root.putObject("stream_options").put("include_usage", true);

        ArrayNode messages = root.putArray("messages");
        for (ChatRequest.ChatMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ChatRequest.ChatTool tool : request.tools()) {
                ObjectNode item = tools.addObject();
                item.put("type", "function");
                ObjectNode function = item.putObject("function");
                function.put("name", tool.name());
                if (tool.description() != null) {
                    function.put("description", tool.description());
                }
                if (tool.parametersJson() != null && !tool.parametersJson().isBlank()) {
                    try {
                        function.set("parameters", JsonUtils.parseTree(tool.parametersJson()));
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException("Chat tool parametersJson must be valid JSON", exception);
                    }
                }
            }
        }
        return JsonUtils.toJsonString(root);
    }

    private static String message(Throwable failure) {
        Throwable value = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }

    interface Transport {
        TransportCall post(URI uri, Map<String, String> headers, String body);
    }

    interface TransportCall {
        CompletableFuture<TransportResponse> future();

        void cancel();
    }

    record TransportResponse(int statusCode, InputStream body) {
        TransportResponse {
            Objects.requireNonNull(body, "body");
        }
    }

    private static final class JdkTransport implements Transport {
        private final HttpClient client;

        private JdkTransport(HttpClient client) {
            this.client = client;
        }

        @Override
        public TransportCall post(URI uri, Map<String, String> headers, String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            CompletableFuture<HttpResponse<InputStream>> raw =
                    client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            CompletableFuture<TransportResponse> mapped =
                    raw.thenApply(response -> new TransportResponse(response.statusCode(), response.body()));
            return new TransportCall() {
                @Override
                public CompletableFuture<TransportResponse> future() {
                    return mapped;
                }

                @Override
                public void cancel() {
                    raw.cancel(true);
                    mapped.cancel(true);
                    raw.thenAccept(response -> closeQuietly(response.body()));
                }
            };
        }
    }

    private static final class DeepSeekChatStream implements ChatStream {
        private final TransportCall call;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile InputStream body;

        private DeepSeekChatStream(TransportCall call) {
            this.call = call;
        }

        private void bind(InputStream body) {
            this.body = body;
            if (cancelled.get()) {
                closeQuietly(body);
            }
        }

        private boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                call.cancel();
                closeBody();
            }
        }

        private void closeBody() {
            InputStream current = body;
            if (current != null) {
                closeQuietly(current);
                body = null;
            }
        }

        private void clearBody() {
            body = null;
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing a cancelled stream is best effort.
        }
    }
}
