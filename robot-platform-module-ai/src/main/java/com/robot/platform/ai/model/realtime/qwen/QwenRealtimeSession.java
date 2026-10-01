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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

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
    private java.util.function.Function<String, String> responseInstructions;
    private TurnStamp preparingTurn;
    private TurnStamp pendingInstructionsTurn;
    private final java.util.Set<TurnStamp> awaitingTranscript = new java.util.HashSet<>();

    synchronized void setResponseInstructions(java.util.function.Function<String, String> instructions) {
        responseInstructions = Objects.requireNonNull(instructions, "instructions");
    }

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
            if (responseInstructions == null) pendingResponseTurns.addLast(turn);
            else awaitingTranscript.add(turn);
        }
        send(codec.encodeAudioCommit());
        if (responseInstructions == null) send(codec.encodeResponseCreate());
        else {
            TurnStamp turn = requireCurrentTurn();
            java.util.concurrent.CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                    .execute(() -> respondWithoutTranscript(turn));
        }
    }

    @Override
    public synchronized void cancelCurrentResponse() {
        requireOpen();
        // There is nothing to cancel while only ASR is in progress.
        if (responseInstructions != null) {
            boolean pending = awaitingTranscript.remove(currentTurn);
            if (Objects.equals(preparingTurn, currentTurn)) { preparingTurn = null; pending = true; }
            if (Objects.equals(pendingInstructionsTurn, currentTurn)) { pendingInstructionsTurn = null; pending = true; }
            if (pending) return;
        }
        send(codec.encodeResponseCancel());
    }

    @Override
    public void close() {
        WebSocket socket;
        synchronized (this) {
            if (closed) return;
            closed = true;
            preparingTurn = null;
            pendingInstructionsTurn = null;
            awaitingTranscript.clear();
            pendingInputTurns.clear();
            pendingResponseTurns.clear();
            inputTurns.clear();
            responseTurns.clear();
            textBuffer.setLength(0);
            socket = webSocket;
        }
        if (socket != null) {
            // Database/session cleanup must not wait for a provider close handshake.
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(3, TimeUnit.SECONDS)
                    .exceptionally(error -> { socket.abort(); return null; });
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        List<StampedEvent> events = List.of();
        synchronized (this) {
            if (closed) return null;
            textBuffer.append(data);
            if (last) {
                String json = textBuffer.toString();
                textBuffer.setLength(0);
                events = decodeServerText(json);
            }
        }
        // Runtime operations acquire the runtime lock before this session lock.
        // Calling back while holding this lock deadlocks with close/interruption.
        for (StampedEvent event : events) {
            emit(event.turn(), event.event());
            if (event.event() instanceof ProviderEvent.TranscriptDone transcript)
                prepareResponse(event.turn(), transcript.text());
        }
        webSocket.request(1);
        return null;
    }

    private void prepareResponse(TurnStamp turn, String text) {
        synchronized (this) {
            if (responseInstructions == null || closed || !awaitingTranscript.contains(turn)) return;
            if (text == null || text.replaceAll("[\\p{P}\\p{Z}\\s]", "").isBlank()) {
                awaitingTranscript.remove(turn);
                return;
            }
            awaitingTranscript.remove(turn);
            preparingTurn = turn;
        }
        // Retrieval/runtime callbacks must never run under the provider session lock.
        final String instructions;
        try {
            instructions = responseInstructions.apply(text);
        } catch (RuntimeException error) {
            emit(turn, new ProviderEvent.ProviderError("context_error", "Could not prepare response context", true));
            return;
        }
        synchronized (this) {
            if (closed || !Objects.equals(currentTurn, turn) || !Objects.equals(preparingTurn, turn)) return;
            preparingTurn = null;
            pendingInstructionsTurn = turn;
            send(codec.encodeSessionUpdate(instructions, model.voiceConfigJson()));
        }
    }

    private void respondWithoutTranscript(TurnStamp turn) {
        try {
            synchronized (this) {
                if (closed || !Objects.equals(currentTurn, turn) || !awaitingTranscript.remove(turn)) return;
                // ASR is auxiliary. A slow transcription must not block native speech response indefinitely.
                // Reset the preceding turn's topic memories before falling back to audio-only inference.
                pendingInstructionsTurn = turn;
                send(codec.encodeSessionUpdate(model.realtimeInstructions(), model.voiceConfigJson()));
            }
        } catch (RuntimeException error) {
            emit(turn, new ProviderEvent.ProviderError("response_start_error", "Could not start response", true));
        }
    }

    private List<StampedEvent> decodeServerText(String json) {
        JsonNode root = JsonUtils.parseTree(json);
        String type = root.path("type").asText("");

        if ("session.updated".equals(type) && pendingInstructionsTurn != null) {
            TurnStamp prepared = pendingInstructionsTurn;
            pendingInstructionsTurn = null;
            if (Objects.equals(currentTurn, prepared)) {
                pendingResponseTurns.addLast(prepared);
                send(codec.encodeResponseCreate());
            }
        }
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

        List<StampedEvent> events = new ArrayList<>();
        for (ProviderEvent event : codec.decodeServerEvent(json))
            events.add(new StampedEvent(resolveTurn(root, event), event));

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
        return events;
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
        emit(currentTurnSnapshot(), new ProviderEvent.ProviderError(
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
        emit(currentTurnSnapshot(), new ProviderEvent.ProviderError(
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

    private synchronized TurnStamp currentTurnSnapshot() {
        return currentTurn;
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

    private record StampedEvent(TurnStamp turn, ProviderEvent event) {
    }
}
