package com.robot.platform.ai.model.realtime.doubao;

import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeTurnListener;
import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

final class DoubaoRealtimeSession implements RealtimeProviderSession, WebSocket.Listener {

    private final DoubaoRealtimeCodec codec;
    private final DoubaoRealtimeCodec.SessionConfig config;
    private final RealtimeProviderListener legacyListener;
    private final RealtimeTurnListener turnListener;
    private final Deque<ByteBuffer> pendingAudio = new ArrayDeque<>();
    private final Deque<TurnStamp> pendingInputTurns = new ArrayDeque<>();
    private final Deque<TurnStamp> chatTurns = new ArrayDeque<>();
    private final Deque<TurnStamp> ttsTurns = new ArrayDeque<>();
    private final ByteArrayOutputStream incoming = new ByteArrayOutputStream();

    private WebSocket webSocket;
    private String sessionId;
    private TurnStamp currentTurn;
    private boolean sessionReady;
    private boolean pendingEndAsr;
    private boolean pendingInterrupt;
    private boolean suppressOutput;
    private boolean closed;

    DoubaoRealtimeSession(DoubaoRealtimeCodec codec,
                          DoubaoRealtimeCodec.SessionConfig config,
                          RealtimeProviderListener listener) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.legacyListener = Objects.requireNonNull(listener, "listener");
        this.turnListener = null;
    }

    DoubaoRealtimeSession(DoubaoRealtimeCodec codec,
                          DoubaoRealtimeCodec.SessionConfig config,
                          RealtimeTurnListener listener) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.legacyListener = null;
        this.turnListener = Objects.requireNonNull(listener, "listener");
    }

    synchronized void bind(WebSocket webSocket) {
        if (this.webSocket == null) {
            this.webSocket = Objects.requireNonNull(webSocket, "webSocket");
            send(codec.encodeStartConnection());
        } else if (this.webSocket != webSocket) {
            throw new IllegalStateException("Doubao WebSocket is already bound");
        }
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
        if (pcm == null) {
            throw new IllegalArgumentException("PCM buffer must not be null");
        }
        ByteBuffer copy = copy(pcm);
        if (!sessionReady) {
            pendingAudio.add(copy);
            return;
        }
        send(codec.encodeAudio(requireSessionId(), copy));
    }

    @Override
    public synchronized void speechStarted() {
        requireOpen();
        suppressOutput = true;
    }

    @Override
    public synchronized void speechStopped() {
        requireOpen();
        suppressOutput = false;
        if (turnListener != null) {
            pendingInputTurns.addLast(requireCurrentTurn());
        }
        if (sessionReady) {
            send(codec.encodeEndAsr(requireSessionId()));
        } else {
            pendingEndAsr = true;
        }
    }

    @Override
    public synchronized void cancelCurrentResponse() {
        requireOpen();
        suppressOutput = true;
        if (sessionReady) {
            send(codec.encodeClientInterrupt(requireSessionId()));
        } else {
            pendingInterrupt = true;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        pendingAudio.clear();
        pendingInputTurns.clear();
        chatTurns.clear();
        ttsTurns.clear();
        if (webSocket != null) {
            if (sessionId != null && !sessionId.isBlank()) {
                sendClosing(codec.encodeFinishSession(sessionId));
            }
            sendClosing(codec.encodeFinishConnection());
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        }
    }

    @Override
    public synchronized void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        webSocket.request(1);
        send(codec.encodeStartConnection());
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        synchronized (this) {
            ByteBuffer copy = data.duplicate();
            byte[] chunk = new byte[copy.remaining()];
            copy.get(chunk);
            incoming.writeBytes(chunk);
            if (last) {
                ByteBuffer frameBytes = ByteBuffer.wrap(incoming.toByteArray());
                incoming.reset();
                handleFrame(codec.decodeFrame(frameBytes));
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        emit(currentTurn, new ProviderEvent.ProviderError(
                "unexpected_text_frame", "Doubao realtime protocol returned an unexpected text frame", false));
        webSocket.request(1);
        return null;
    }

    @Override
    public synchronized CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closed = true;
        pendingAudio.clear();
        pendingInputTurns.clear();
        chatTurns.clear();
        ttsTurns.clear();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        emit(currentTurn, new ProviderEvent.ProviderError(
                "transport_error",
                error == null || error.getMessage() == null ? "Doubao realtime transport error" : error.getMessage(),
                true));
    }

    private void handleFrame(DoubaoRealtimeCodec.DecodedFrame frame) {
        if (frame.event() == DoubaoRealtimeCodec.EVENT_CONNECTION_STARTED) {
            if (frame.sessionId() == null || frame.sessionId().isBlank()) {
                emit(currentTurn, new ProviderEvent.ProviderError(
                        "missing_session_id", "Doubao StartConnection response did not contain session id", false));
                return;
            }
            sessionId = frame.sessionId();
            send(codec.encodeStartSession(sessionId, config));
            return;
        }
        if (frame.event() == DoubaoRealtimeCodec.EVENT_SESSION_STARTED) {
            sessionReady = true;
            flushPendingAudio();
            flushPendingControls();
            return;
        }

        List<ProviderEvent> events = codec.normalize(frame);
        for (ProviderEvent event : events) {
            if (event instanceof ProviderEvent.TranscriptDelta) {
                emit(inputTurn(), event);
                continue;
            }
            if (event instanceof ProviderEvent.TranscriptDone) {
                TurnStamp completedInput = completeInputTurn();
                emit(completedInput, event);
                if (turnListener != null && completedInput != null) {
                    chatTurns.addLast(completedInput);
                }
                continue;
            }

            TurnStamp turn = turnForAssistantEvent(frame.event(), event);
            boolean assistantOutput = isAssistantOutput(event);
            if (!(suppressOutput && assistantOutput)) {
                emit(turn, event);
            }

            if (frame.event() == DoubaoRealtimeCodec.EVENT_CHAT_ENDED) {
                completeChatTurn(turn);
            } else if (frame.event() == DoubaoRealtimeCodec.EVENT_TTS_ENDED) {
                completeTtsTurn(turn);
            }
        }
    }

    private TurnStamp inputTurn() {
        TurnStamp pending = pendingInputTurns.peekFirst();
        return pending == null ? currentTurn : pending;
    }

    private TurnStamp completeInputTurn() {
        TurnStamp pending = pendingInputTurns.pollFirst();
        return pending == null ? currentTurn : pending;
    }

    private TurnStamp turnForAssistantEvent(int eventCode, ProviderEvent event) {
        if (eventCode == DoubaoRealtimeCodec.EVENT_TTS_SENTENCE_START
                || eventCode == DoubaoRealtimeCodec.EVENT_TTS_SENTENCE_END
                || eventCode == DoubaoRealtimeCodec.EVENT_TTS_RESPONSE
                || eventCode == DoubaoRealtimeCodec.EVENT_TTS_ENDED
                || event instanceof ProviderEvent.AudioDelta
                || event instanceof ProviderEvent.AudioDone) {
            TurnStamp tts = ttsTurns.peekFirst();
            if (tts != null) {
                return tts;
            }
            TurnStamp chat = chatTurns.peekFirst();
            return chat == null ? currentTurn : chat;
        }

        TurnStamp chat = chatTurns.peekFirst();
        return chat == null ? currentTurn : chat;
    }

    private void completeChatTurn(TurnStamp emittedTurn) {
        TurnStamp pending = chatTurns.peekFirst();
        if (pending != null && pending.equals(emittedTurn)) {
            chatTurns.removeFirst();
            ttsTurns.addLast(pending);
        }
    }

    private void completeTtsTurn(TurnStamp emittedTurn) {
        TurnStamp pending = ttsTurns.peekFirst();
        if (pending != null && pending.equals(emittedTurn)) {
            ttsTurns.removeFirst();
        }
    }

    private void flushPendingAudio() {
        while (!pendingAudio.isEmpty()) {
            send(codec.encodeAudio(requireSessionId(), pendingAudio.removeFirst()));
        }
    }

    private void flushPendingControls() {
        if (pendingEndAsr) {
            pendingEndAsr = false;
            send(codec.encodeEndAsr(requireSessionId()));
        }
        if (pendingInterrupt) {
            pendingInterrupt = false;
            send(codec.encodeClientInterrupt(requireSessionId()));
        }
    }

    private static boolean isAssistantOutput(ProviderEvent event) {
        return event instanceof ProviderEvent.TextDelta
                || event instanceof ProviderEvent.TextDone
                || event instanceof ProviderEvent.AudioDelta
                || event instanceof ProviderEvent.AudioDone
                || event instanceof ProviderEvent.ToolCall
                || event instanceof ProviderEvent.ToolCallDelta;
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
            throw new IllegalStateException("Doubao realtime turn context is not initialized");
        }
        return currentTurn;
    }

    private void send(ByteBuffer frame) {
        requireOpen();
        webSocket.sendBinary(frame, true).join();
    }

    private void sendClosing(ByteBuffer frame) {
        webSocket.sendBinary(frame, true).join();
    }

    private String requireSessionId() {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("Doubao realtime session id is not available");
        }
        return sessionId;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doubao realtime session is closed");
        }
        if (webSocket == null) {
            throw new IllegalStateException("Doubao realtime WebSocket is not connected");
        }
    }

    private static ByteBuffer copy(ByteBuffer source) {
        ByteBuffer input = source.duplicate();
        ByteBuffer copy = ByteBuffer.allocate(input.remaining());
        copy.put(input).flip();
        return copy.asReadOnlyBuffer();
    }

    private record TurnStamp(String turnId, long generation) {
    }
}
