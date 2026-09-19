package com.robot.platform.ai.model.realtime.doubao;

import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
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
    private final RealtimeProviderListener listener;
    private final Deque<ByteBuffer> pendingAudio = new ArrayDeque<>();
    private final ByteArrayOutputStream incoming = new ByteArrayOutputStream();

    private WebSocket webSocket;
    private String sessionId;
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
        this.listener = Objects.requireNonNull(listener, "listener");
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
                DoubaoRealtimeCodec.DecodedFrame frame = codec.decodeFrame(frameBytes);
                handleFrame(frame);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        listener.onEvent(new ProviderEvent.ProviderError(
                "unexpected_text_frame", "Doubao realtime protocol returned an unexpected text frame", false));
        webSocket.request(1);
        return null;
    }

    @Override
    public synchronized CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closed = true;
        pendingAudio.clear();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        listener.onEvent(new ProviderEvent.ProviderError(
                "transport_error",
                error == null || error.getMessage() == null ? "Doubao realtime transport error" : error.getMessage(),
                true));
    }

    private void handleFrame(DoubaoRealtimeCodec.DecodedFrame frame) {
        if (frame.event() == DoubaoRealtimeCodec.EVENT_CONNECTION_STARTED) {
            if (frame.sessionId() == null || frame.sessionId().isBlank()) {
                listener.onEvent(new ProviderEvent.ProviderError(
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
            if (suppressOutput && isAssistantOutput(event)) {
                continue;
            }
            listener.onEvent(event);
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
                || event instanceof ProviderEvent.AudioDone;
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

}