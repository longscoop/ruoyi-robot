package com.robot.platform.ai.model.tts.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.TtsClient;
import com.robot.platform.ai.model.client.TtsListener;
import com.robot.platform.ai.model.client.TtsRequest;
import com.robot.platform.ai.model.client.TtsStream;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class QwenTtsClient implements TtsClient {

    private final WebSocketConnector connector;

    public QwenTtsClient() {
        this(new JdkWebSocketConnector(HttpClient.newHttpClient()));
    }

    QwenTtsClient(WebSocketConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public String providerType() {
        return "QWEN";
    }

    @Override
    public TtsStream stream(TtsRequest request, TtsListener listener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(listener, "listener");
        validate(request.model());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + request.model().credential());
        JsonNode provider = objectOrEmpty(request.model().providerConfigJson());
        String workspaceId = text(provider, "workspaceId");
        if (workspaceId != null && !workspaceId.isBlank()) {
            headers.put("X-DashScope-WorkSpace", workspaceId);
        }

        Session session = new Session(request, listener);
        WebSocket socket = connector.connect(uri(request.model()), Map.copyOf(headers), session).join();
        session.bind(socket);
        return session;
    }

    private static void validate(ResolvedModel model) {
        if (!"QWEN".equals(model.providerType())) {
            throw new IllegalArgumentException("Qwen TTS client requires providerType=QWEN");
        }
        if (!"TTS".equals(model.modelType())) {
            throw new IllegalArgumentException("Qwen TTS client requires modelType=TTS");
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException("Qwen TTS credential must not be blank");
        }
    }

    private static URI uri(ResolvedModel model) {
        String base = model.baseUrl();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + "model="
                + URLEncoder.encode(model.modelCode(), StandardCharsets.UTF_8).replace("+", "%20"));
    }

    interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers, WebSocket.Listener listener);
    }

    private record JdkWebSocketConnector(HttpClient client) implements WebSocketConnector {
        @Override
        public CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers,
                                                    WebSocket.Listener listener) {
            WebSocket.Builder builder = client.newWebSocketBuilder();
            headers.forEach(builder::header);
            return builder.buildAsync(uri, listener);
        }
    }

    private static final class Session implements TtsStream, WebSocket.Listener {
        private final TtsRequest request;
        private final TtsListener listener;
        private final StringBuilder incoming = new StringBuilder();

        private WebSocket socket;
        private boolean configured;
        private boolean textSent;
        private boolean finishSent;
        private boolean closed;

        private Session(TtsRequest request, TtsListener listener) {
            this.request = request;
            this.listener = listener;
        }

        synchronized void bind(WebSocket socket) {
            if (this.socket == null) {
                this.socket = socket;
            } else if (this.socket != socket) {
                throw new IllegalStateException("Qwen TTS WebSocket already bound");
            }
        }

        @Override
        public synchronized void cancel() {
            if (closed) {
                return;
            }
            if (socket != null) {
                if (!finishSent) {
                    sendType("input_text_buffer.clear");
                    sendType("session.finish");
                    finishSent = true;
                }
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
            }
            closed = true;
        }

        @Override
        public synchronized void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (this) {
                incoming.append(data);
                if (last) {
                    String json = incoming.toString();
                    incoming.setLength(0);
                    handle(json);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            listener.onEvent(new ProviderEvent.ProviderError(
                    "unexpected_binary_frame", "Qwen TTS returned unexpected binary output", false));
            webSocket.request(1);
            return null;
        }

        @Override
        public synchronized CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onEvent(new ProviderEvent.ProviderError(
                    "transport_error", error == null || error.getMessage() == null
                    ? "Qwen TTS transport error" : error.getMessage(), true));
        }

        private void handle(String json) {
            JsonNode root = JsonUtils.parseTree(json);
            String type = root.path("type").asText("");
            switch (type) {
                case "session.created" -> sendSessionUpdate();
                case "session.updated" -> sendTextAndCommit();
                case "response.audio.delta" -> {
                    String value = root.path("delta").asText("");
                    try {
                        listener.onEvent(new ProviderEvent.AudioDelta(
                                ByteBuffer.wrap(Base64.getDecoder().decode(value))));
                    } catch (IllegalArgumentException exception) {
                        listener.onEvent(new ProviderEvent.ProviderError(
                                "invalid_audio_base64", "Qwen TTS returned invalid audio payload", false));
                    }
                }
                case "response.audio.done" -> {
                    listener.onEvent(new ProviderEvent.AudioDone());
                    if (!finishSent) {
                        sendType("session.finish");
                        finishSent = true;
                    }
                }
                case "error" -> listener.onEvent(new ProviderEvent.ProviderError(
                        root.path("error").path("code").asText("tts_error"),
                        root.path("error").path("message").asText("Qwen TTS error"),
                        false));
                case "session.finished" -> {
                    if (!closed) {
                        closed = true;
                        socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
                    }
                }
                default -> {
                }
            }
        }

        private void sendSessionUpdate() {
            if (configured) {
                return;
            }
            configured = true;
            JsonNode config = objectOrEmpty(request.model().modelConfigJson());
            String voice = text(config, "voice");
            if (voice == null || voice.isBlank()) {
                throw new IllegalArgumentException("Qwen TTS model config requires voice");
            }
            ObjectNode root = event("session.update");
            ObjectNode session = root.putObject("session");
            session.put("voice", voice);
            session.put("mode", "commit");
            session.put("response_format", text(config, "responseFormat") == null
                    ? "pcm" : text(config, "responseFormat"));
            session.put("sample_rate", intValue(config, "sampleRate", 24000));
            String language = text(config, "languageType");
            if (language != null && !language.isBlank()) {
                session.put("language_type", language);
            }
            String instructions = text(config, "instructions");
            if (instructions != null && !instructions.isBlank()) {
                session.put("instructions", instructions);
            }
            send(root);
        }

        private void sendTextAndCommit() {
            if (textSent) {
                return;
            }
            textSent = true;
            ObjectNode append = event("input_text_buffer.append");
            append.put("text", request.text());
            send(append);
            sendType("input_text_buffer.commit");
        }

        private void sendType(String type) {
            send(event(type));
        }

        private ObjectNode event(String type) {
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
            root.put("event_id", "event_" + UUID.randomUUID());
            root.put("type", type);
            return root;
        }

        private void send(ObjectNode root) {
            socket.sendText(JsonUtils.toJsonString(root), true).join();
        }
    }

    private static JsonNode objectOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        JsonNode root = JsonUtils.parseTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Qwen TTS config must be a JSON object");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : fallback;
    }
}
