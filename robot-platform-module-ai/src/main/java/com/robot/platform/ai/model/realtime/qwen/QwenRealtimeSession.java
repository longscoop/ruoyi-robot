package com.robot.platform.ai.model.realtime.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeTurnListener;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

final class QwenRealtimeSession implements RealtimeProviderSession, WebSocket.Listener {

    private final QwenRealtimeCodec codec;
    private final ResolvedModel model;
    private final RealtimeProviderListener legacyListener;
    private final RealtimeTurnListener turnListener;
    private final StringBuilder textBuffer = new StringBuilder();
    private final Deque<TurnStamp> pendingInputTurns = new ArrayDeque<>();
    private final Deque<TurnStamp> pendingResponseTurns = new ArrayDeque<>();
    private final Map<String, TurnStamp> inputTurns = new HashMap<>();
    private final Map<String, TurnStamp> responseTurns = new HashMap<>();

    private WebSocket webSocket;
    private TurnStamp currentTurn;
    private boolean closed;

    QwenRealtimeSession(QwenRealtimeCodec codec, ResolvedModel model, RealtimeProviderListener listener) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.model = Objects.requireNonNull(model, "model");
        this.legacyListener = Objects.requireNonNull(listener, "listener");
        this.turnListener = null;
    }

    QwenRealtimeSession(QwenRealtimeCodec codec, ResolvedModel model, RealtimeTurnListener listener) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.model = Objects.requireNonNull(model, "model");
        this.legacyListener = null;
        this.turnListener = Objects.requireNonNull(listener, "listener");
    }

    synchronized void bind(WebSocket webSocket) {
        if (this.webSocket != null) {
            throw new IllegalStateException("Qwen WebSocket is already bound");
        }
        this.webSocket = Objects.requireNonNull(webSocket, "webSocket");
        send(codec.encodeSessionUpdate(model.realtimeInstructions(), model.voiceConfigJson()));
    }

    @Override
    public synchronized void beginTurn(String turnId, long generation) {
        if (turnId == null || turnId.isBlank() || generation <= 0) {
            throw new IllegalArgumentException("Valid turnId and generation are required");
        }
        currentTurn = new TurnStamp(turnId, generation);
    }

    @Override
    public synchronized void appendAudio(ByteBuffer pcm) {
        requireOpen();
        send(codec.encodeAudioAppend(pcm));
    }

    @Override
    public synchronized void speechStarted() {
        requireOpen();
        send(codec.encodeSpeechStarted());
    }

    @Override
    public synchronized void speechStopped() {
        requireOpen();
        if (turnListener != null) {
            TurnStamp turn = requireCurrentTurn();
            pendingInputTurns.addLast(turn);
            pendingResponseTurns.addLast(turn);
        }
        send(codec.encodeAudioCommit());
        send(codec.encodeResponseCreate());
    }

    @Override
    public synchronized void cancelCurrentResponse() {
        requireOpen();
        send(codec.encodeResponseCancel());
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        pendingInputTurns.clear();
        pendingResponseTurns.clear();
        inputTurns.clear();
        responseTurns.clear();
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (this) {
            textBuffer.append(data);
            if (last) {
                String json = textBuffer.toString();
                textBuffer.setLength(0);
                handleServerText(json);
            }
        }
        webSocket.request(1);
        return null;
    }

    private void handleServerText(String json) {
        JsonNode root = JsonUtils.parseTree(json);
        String type = root.path("type").asText("");

        if ("input_audio_buffer.committed".equals(type) && turnListener != null) {
            TurnStamp turn = pendingInputTurns.pollFirst();
            String itemId = root.path("item_id").asText("");
            if (turn != null && !itemId.isBlank()) {
                inputTurns.put(itemId, turn);
            }
        } else if ("response.created".equals(type) && turnListener != null) {
            TurnStamp turn = pendingResponseTurns.pollFirst();
            String responseId = root.path("response").path("id").asText("");
            if (turn != null && !responseId.isBlank()) {
                responseTurns.put(responseId, turn);
            }
        }

        for (ProviderEvent event : codec.decodeServerEvent(json)) {
            emit(resolveTurn(root, event), event);
        }

        if ("conversation.item.input_audio_transcription.completed".equals(type)) {
            String itemId = root.path("item_id").asText("");
            if (!itemId.isBlank()) {
                inputTurns.remove(itemId);
            }
        } else if ("response.done".equals(type)) {
            String responseId = responseId(root);
            if (!responseId.isBlank()) {
                responseTurns.remove(responseId);
            }
        }
    }

    private TurnStamp resolveTurn(JsonNode root, ProviderEvent event) {
        if (turnListener == null) {
            return null;
        }
        if (event instanceof ProviderEvent.TranscriptDelta
                || event instanceof ProviderEvent.TranscriptDone) {
            String itemId = root.path("item_id").asText("");
            return itemId.isBlank() ? currentTurn : inputTurns.getOrDefault(itemId, currentTurn);
        }
        String responseId = responseId(root);
        return responseId.isBlank() ? currentTurn : responseTurns.getOrDefault(responseId, currentTurn);
    }

    private static String responseId(JsonNode root) {
        String direct = root.path("response_id").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        return root.path("response").path("id").asText("");
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        emit(currentTurn, new ProviderEvent.ProviderError(
                "unexpected_binary_frame", "Qwen realtime protocol returned an unexpected binary frame", false));
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        synchronized (this) {
            closed = true;
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        emit(currentTurn, new ProviderEvent.ProviderError(
                "transport_error",
                error == null || error.getMessage() == null ? "Qwen realtime transport error" : error.getMessage(),
                true));
    }

    private void emit(TurnStamp turn, ProviderEvent event) {
        if (turnListener != null) {
            if (turn == null) {
                turnListener.onEvent(null, 0L, event);
            } else {
                turnListener.onEvent(turn.turnId(), turn.generation(), event);
            }
        } else {
            legacyListener.onEvent(event);
        }
    }

    private TurnStamp requireCurrentTurn() {
        if (currentTurn == null) {
            throw new IllegalStateException("Qwen realtime turn context is not initialized");
        }
        return currentTurn;
    }

    private void send(String json) {
        requireOpen();
        webSocket.sendText(json, true).join();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Qwen realtime session is closed");
        }
        if (webSocket == null) {
            throw new IllegalStateException("Qwen realtime WebSocket is not connected");
        }
    }

    private record TurnStamp(String turnId, long generation) {
    }
}
