package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

final class QwenRealtimeSession implements RealtimeProviderSession, WebSocket.Listener {

    private final QwenRealtimeCodec codec;
    private final ResolvedModel model;
    private final RealtimeProviderListener listener;
    private final StringBuilder textBuffer = new StringBuilder();

    private WebSocket webSocket;
    private boolean closed;

    QwenRealtimeSession(QwenRealtimeCodec codec, ResolvedModel model, RealtimeProviderListener listener) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.model = Objects.requireNonNull(model, "model");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    synchronized void bind(WebSocket webSocket) {
        if (this.webSocket != null) {
            throw new IllegalStateException("Qwen WebSocket is already bound");
        }
        this.webSocket = Objects.requireNonNull(webSocket, "webSocket");
        send(codec.encodeSessionUpdate(model.realtimeInstructions(), model.voiceConfigJson()));
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
                for (ProviderEvent event : codec.decodeServerEvent(json)) {
                    listener.onEvent(event);
                }
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        listener.onEvent(new ProviderEvent.ProviderError(
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
        listener.onEvent(new ProviderEvent.ProviderError(
                "transport_error",
                error == null || error.getMessage() == null ? "Qwen realtime transport error" : error.getMessage(),
                true));
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
}
