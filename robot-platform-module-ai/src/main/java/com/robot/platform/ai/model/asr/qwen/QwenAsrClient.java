package com.robot.platform.ai.model.asr.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.AsrClient;
import com.robot.platform.ai.model.client.AsrListener;
import com.robot.platform.ai.model.client.AsrSession;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class QwenAsrClient implements AsrClient {

    private final WebSocketConnector connector;

    public QwenAsrClient() {
        this(new JdkWebSocketConnector(HttpClient.newHttpClient()));
    }

    QwenAsrClient(WebSocketConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public String providerType() {
        return "QWEN";
    }

    @Override
    public AsrSession open(ResolvedModel model, AsrListener listener) {
        validate(model);
        Objects.requireNonNull(listener, "listener");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + model.credential());
        JsonNode provider = objectOrEmpty(model.providerConfigJson());
        String workspaceId = text(provider, "workspaceId");
        if (workspaceId != null && !workspaceId.isBlank()) {
            headers.put("X-DashScope-WorkSpace", workspaceId);
        }

        Session session = new Session(model, listener);
        WebSocket socket = connector.connect(URI.create(model.baseUrl()), Map.copyOf(headers), session).join();
        session.bind(socket);
        return session;
    }

    private static void validate(ResolvedModel model) {
        Objects.requireNonNull(model, "model");
        if (!"QWEN".equals(model.providerType())) {
            throw new IllegalArgumentException("Qwen ASR client requires providerType=QWEN");
        }
        if (!"ASR".equals(model.modelType())) {
            throw new IllegalArgumentException("Qwen ASR client requires modelType=ASR");
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException("Qwen ASR credential must not be blank");
        }
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

    private static final class Session implements AsrSession, WebSocket.Listener {
        private static final int MAX_PENDING_AUDIO_BYTES = 256 * 1024;

        private final ResolvedModel model;
        private final AsrListener listener;
        private final String taskId = UUID.randomUUID().toString();
        private final Deque<ByteBuffer> pendingAudio = new ArrayDeque<>();
        private final StringBuilder textBuffer = new StringBuilder();

        private WebSocket socket;
        private int pendingAudioBytes;
        private boolean runTaskSent;
        private boolean ready;
        private boolean finishPending;
        private boolean finishSent;
        private boolean cancelled;
        private boolean closed;

        private Session(ResolvedModel model, AsrListener listener) {
            this.model = model;
            this.listener = listener;
        }

        synchronized void bind(WebSocket socket) {
            if (this.socket == null) {
                this.socket = socket;
            } else if (this.socket != socket) {
                throw new IllegalStateException("Qwen ASR WebSocket already bound");
            }
            sendRunTask();
        }

        @Override
        public synchronized void appendAudio(ByteBuffer pcm) {
            requireOpen();
            if (pcm == null) {
                throw new IllegalArgumentException("PCM buffer must not be null");
            }
            ByteBuffer copy = copy(pcm);
            if (!ready) {
                int bytes = copy.remaining();
                if (bytes > MAX_PENDING_AUDIO_BYTES - pendingAudioBytes) {
                    throw new IllegalStateException("Qwen ASR pending audio buffer is full");
                }
                pendingAudio.add(copy);
                pendingAudioBytes += bytes;
            } else {
                socket.sendBinary(copy, true).join();
            }
        }

        @Override
        public synchronized void speechStarted() {
            requireOpen();
        }

        @Override
        public synchronized void speechStopped() {
            requireOpen();
            if (ready) {
                sendFinishTask();
            } else {
                finishPending = true;
            }
        }

        @Override
        public synchronized void cancel() {
            if (closed) {
                return;
            }
            cancelled = true;
            pendingAudio.clear();
            pendingAudioBytes = 0;
            if (ready && !finishSent) {
                sendFinishTask();
            }
            closed = true;
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
            }
        }

        @Override
        public synchronized void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (this) {
                textBuffer.append(data);
                if (last) {
                    String json = textBuffer.toString();
                    textBuffer.setLength(0);
                    handleServer(json);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (!cancelled) {
                listener.onEvent(new ProviderEvent.ProviderError(
                        "unexpected_binary_frame", "Qwen ASR returned unexpected binary output", false));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public synchronized CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            pendingAudio.clear();
            pendingAudioBytes = 0;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (!cancelled) {
                listener.onEvent(new ProviderEvent.ProviderError(
                        "transport_error", error == null || error.getMessage() == null
                        ? "Qwen ASR transport error" : error.getMessage(), true));
            }
        }

        private void handleServer(String json) {
            JsonNode root = JsonUtils.parseTree(json);
            String event = root.path("header").path("event").asText("");
            switch (event) {
                case "task-started" -> {
                    ready = true;
                    while (!pendingAudio.isEmpty()) {
                        ByteBuffer pending = pendingAudio.removeFirst();
                        pendingAudioBytes -= pending.remaining();
                        socket.sendBinary(pending, true).join();
                    }
                    pendingAudioBytes = 0;
                    if (finishPending) {
                        finishPending = false;
                        sendFinishTask();
                    }
                }
                case "result-generated" -> {
                    if (cancelled) {
                        return;
                    }
                    JsonNode sentence = root.path("payload").path("output").path("sentence");
                    if (sentence.path("heartbeat").asBoolean(false)) {
                        return;
                    }
                    String value = sentence.path("text").asText("");
                    listener.onEvent(sentence.path("sentence_end").asBoolean(false)
                            ? new ProviderEvent.TranscriptDone(value)
                            : new ProviderEvent.TranscriptDelta(value));
                }
                case "task-failed" -> {
                    if (!cancelled) {
                        listener.onEvent(new ProviderEvent.ProviderError(
                                root.path("header").path("error_code").asText("task_failed"),
                                root.path("header").path("error_message").asText("Qwen ASR task failed"),
                                false));
                    }
                }
                case "task-finished" -> {
                    pendingAudio.clear();
                    pendingAudioBytes = 0;
                    if (!closed) {
                        closed = true;
                        socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
                    }
                }
                default -> {
                }
            }
        }

        private void sendRunTask() {
            if (runTaskSent) {
                return;
            }
            runTaskSent = true;
            socket.sendText(runTaskJson(), true).join();
        }

        private String runTaskJson() {
            JsonNode config = objectOrEmpty(model.modelConfigJson());
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
            ObjectNode header = root.putObject("header");
            header.put("action", "run-task");
            header.put("task_id", taskId);
            header.put("streaming", "duplex");

            ObjectNode payload = root.putObject("payload");
            payload.put("task_group", "audio");
            payload.put("task", "asr");
            payload.put("function", "recognition");
            payload.put("model", model.modelCode());
            ObjectNode parameters = payload.putObject("parameters");
            parameters.put("format", text(config, "format") == null ? "pcm" : text(config, "format"));
            parameters.put("sample_rate", intValue(config, "sampleRate", 16000));
            JsonNode languageHints = config.get("languageHints");
            if (languageHints != null && languageHints.isArray()) {
                parameters.set("language_hints", languageHints);
            }
            payload.putObject("input");
            return JsonUtils.toJsonString(root);
        }

        private void sendFinishTask() {
            if (finishSent) {
                return;
            }
            finishSent = true;
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
            ObjectNode header = root.putObject("header");
            header.put("action", "finish-task");
            header.put("task_id", taskId);
            header.put("streaming", "duplex");
            root.putObject("payload").putObject("input");
            socket.sendText(JsonUtils.toJsonString(root), true).join();
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Qwen ASR session is closed");
            }
        }
    }

    private static JsonNode objectOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        JsonNode root = JsonUtils.parseTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Qwen ASR config must be a JSON object");
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

    private static ByteBuffer copy(ByteBuffer source) {
        ByteBuffer input = source.duplicate();
        ByteBuffer copy = ByteBuffer.allocate(input.remaining());
        copy.put(input).flip();
        return copy.asReadOnlyBuffer();
    }
}
