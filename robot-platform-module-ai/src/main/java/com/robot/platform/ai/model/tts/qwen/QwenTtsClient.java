package com.robot.platform.ai.model.tts.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.TtsClient;
import com.robot.platform.ai.model.client.TtsListener;
import com.robot.platform.ai.model.client.TtsRequest;
import com.robot.platform.ai.model.client.TtsStream;
import com.robot.platform.ai.model.client.TtsSession;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
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

    @org.springframework.beans.factory.annotation.Autowired
    public QwenTtsClient(@org.springframework.beans.factory.annotation.Qualifier("aiModelHttpClient") HttpClient client) {
        this(new JdkWebSocketConnector(client));
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
        return connect(request.model(), request.text(), listener, false);
    }

    @Override
    public TtsSession openSession(ResolvedModel model, TtsListener listener) {
        return connect(model, null, listener, true);
    }

    private Session connect(ResolvedModel model, String initialText, TtsListener listener, boolean incremental) {
        Objects.requireNonNull(listener, "listener");
        validate(model);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + model.credential());
        JsonNode provider = objectOrEmpty(model.providerConfigJson());
        String workspaceId = text(provider, "workspaceId");
        if (workspaceId != null && !workspaceId.isBlank()) headers.put("X-DashScope-WorkSpace", workspaceId);
        Session session = new Session(model, initialText, listener, incremental);
        connector.connect(uri(model), Map.copyOf(headers), session)
                .orTimeout(10, TimeUnit.SECONDS).whenComplete((socket, error) -> {
                    if (error != null) session.fail("tts_connect_failed", error);
                    else session.bind(socket);
                });
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
            WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
            headers.forEach(builder::header);
            return builder.buildAsync(uri, listener);
        }
    }

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "qwen-tts-timeout"); thread.setDaemon(true); return thread;
    });

    private static final class Session implements TtsSession, WebSocket.Listener {
        private final ResolvedModel model;
        private final TtsListener listener;
        private final boolean incremental;
        private final StringBuilder incoming = new StringBuilder();
        private final ArrayDeque<String> texts = new ArrayDeque<>();
        private final ArrayDeque<ProviderEvent> events = new ArrayDeque<>();
        private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);
        private ScheduledFuture<?> timeout;
        private WebSocket socket;
        private boolean configured, ready, active, inputDone, finishSent, completed, closed, delivering;
        private int queuedChars;
        private String lastEvent = "connecting";
        private final long startedNanos = System.nanoTime();
        private String failureType = "none";
        private int handshakeStatus;
        private boolean retryable = true;

        private Session(ResolvedModel model, String initialText, TtsListener listener, boolean incremental) {
            this.model = model; this.listener = listener; this.incremental = incremental;
            if (!incremental) { texts.add(initialText); queuedChars = initialText.length(); inputDone = true; }
            armTimeout();
        }

        synchronized void bind(WebSocket value) {
            if (closed) { value.abort(); return; }
            socket = value;
        }

        @Override public void appendText(String text) {
            if (text == null || text.codePoints().noneMatch(Character::isLetterOrDigit)) return;
            synchronized (this) {
                if (closed || inputDone) throw new IllegalStateException("TTS input is closed");
                if (texts.size() >= 64 || queuedChars + text.length() > 8192) throw new IllegalStateException("TTS input queue is full");
                texts.addLast(text); queuedChars += text.length(); pump();
            }
        }

        @Override public synchronized void finishInput() {
            if (closed || inputDone) return;
            inputDone = true; pump();
        }

        private void pump() {
            if (!ready || closed || active || finishSent) return;
            if (!texts.isEmpty()) {
                String text = texts.removeFirst(); queuedChars -= text.length(); active = true;
                ObjectNode append = event("input_text_buffer.append"); append.put("text", text);
                send(append); sendType("input_text_buffer.commit"); armTimeout();
            } else if (inputDone) {
                finishSent = true; sendType("session.finish"); armTimeout();
            } else if (timeout != null) {
                // Warm connection may wait while the user is speaking (up to 60s).
                timeout.cancel(false);
            }
        }

        @Override public synchronized void cancel() {
            if (closed) return;
            closed = true; texts.clear(); events.clear();
            if (timeout != null) timeout.cancel(false);
            // Abort immediately; waiting on provider sends must never block barge-in.
            if (socket != null) socket.abort();
        }

        @Override public synchronized void onOpen(WebSocket webSocket) {
            if (closed) { webSocket.abort(); return; }
            socket = webSocket; webSocket.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (this) {
                if (!closed) {
                    incoming.append(data);
                    if (last) {
                        String json = incoming.toString(); incoming.setLength(0);
                        try { handle(json); } catch (RuntimeException invalid) { failLocked("tts_invalid_event"); }
                    }
                }
            }
            dispatch(); webSocket.request(1); return null;
        }

        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            fail("tts_unexpected_binary"); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            synchronized (this) { if (!closed && !completed) failLocked("tts_closed_early"); }
            dispatch(); return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) { fail("tts_transport_error", error); }

        private void handle(String json) {
            JsonNode root = JsonUtils.parseTree(json);
            lastEvent = root.path("type").asText("");
            switch (lastEvent) {
                case "session.created" -> sendSessionUpdate();
                case "session.updated" -> { ready = true; pump(); }
                case "response.audio.delta" -> {
                    events.add(new ProviderEvent.AudioDelta(ByteBuffer.wrap(Base64.getDecoder().decode(root.path("delta").asText("")))));
                    armTimeout();
                }
                case "response.audio.done" -> {
                    // Legacy one-shot clients retain their original completion event.
                    if (!incremental && !completed) {
                        completed = true; events.add(new ProviderEvent.AudioDone());
                        finishSent = true; sendType("session.finish");
                    }
                }
                case "response.done" -> { active = false; pump(); }
                case "session.finished" -> {
                    if (!inputDone || !texts.isEmpty()) { failLocked("tts_finished_early"); break; }
                    if (!completed) { completed = true; events.add(new ProviderEvent.AudioDone()); }
                    if (timeout != null) timeout.cancel(false);
                    closed = true;
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "");
                }
                case "error" -> { retryable = false; failLocked("tts_provider_error"); }
                default -> { }
            }
        }

        private void sendSessionUpdate() {
            if (configured) return;
            configured = true;
            JsonNode config = objectOrEmpty(model.modelConfigJson());
            String voice = text(config, "voice");
            if (voice == null || voice.isBlank()) throw new IllegalArgumentException("Qwen TTS model config requires voice");
            ObjectNode root = event("session.update");
            ObjectNode session = root.putObject("session");
            session.put("voice", voice); session.put("mode", "commit");
            session.put("response_format", text(config, "responseFormat") == null ? "pcm" : text(config, "responseFormat"));
            session.put("sample_rate", intValue(config, "sampleRate", 24000));
            if (text(config, "languageType") != null) session.put("language_type", text(config, "languageType"));
            if (text(config, "instructions") != null) session.put("instructions", text(config, "instructions"));
            send(root);
        }
        private void sendType(String type) { send(event(type)); }
        private ObjectNode event(String type) {
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
            root.put("event_id", "event_" + UUID.randomUUID()); root.put("type", type); return root;
        }
        private void send(ObjectNode root) {
            String json = JsonUtils.toJsonString(root);
            sends = sends.thenCompose(ignored -> socket.sendText(json, true));
            sends.whenCompleteAsync((ignored, error) -> { if (error != null) fail("tts_send_failed", error); });
        }
        private synchronized void armTimeout() {
            if (timeout != null) timeout.cancel(false);
            timeout = WATCHDOG.schedule(() -> fail("tts_idle_timeout"), 15, TimeUnit.SECONDS);
        }
        private void fail(String code) { synchronized (this) { failLocked(code); } dispatch(); }
        private void fail(String code, Throwable error) {
            synchronized (this) {
                Throwable cause = error;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null)
                    cause = cause.getCause();
                // Exception messages, response bodies and headers may contain credentials. Record only
                // the exception class and HTTP status, which still distinguish timeout/TLS/auth/quota.
                failureType = cause.getClass().getSimpleName();
                if (cause instanceof java.net.http.WebSocketHandshakeException handshake) {
                    handshakeStatus = handshake.getResponse().statusCode();
                    retryable = handshakeStatus == 408 || handshakeStatus == 429 || handshakeStatus >= 500;
                }
                failLocked(code);
            }
            dispatch();
        }
        private void failLocked(String code) {
            if (closed) return;
            closed = true;
            if (timeout != null) timeout.cancel(false);
            org.slf4j.LoggerFactory.getLogger(QwenTtsClient.class).warn(
                    "TTS stopped: code={} cause={} httpStatus={} elapsedMs={} lastEvent={} ready={} active={} queued={} inputDone={}",
                    code, failureType, handshakeStatus, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                    lastEvent, ready, active, texts.size(), inputDone);
            texts.clear();
            events.add(new ProviderEvent.ProviderError(code, "Qwen TTS session failed", retryable));
            if (socket != null) socket.abort();
        }
        private void dispatch() {
            synchronized (this) { if (delivering) return; delivering = true; }
            try {
                while (true) {
                    ProviderEvent next;
                    synchronized (this) {
                        next = events.pollFirst();
                        if (next == null) { delivering = false; return; }
                    }
                    listener.onEvent(next);
                }
            } catch (RuntimeException error) { synchronized (this) { delivering = false; } throw error; }
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
