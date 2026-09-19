package com.robot.platform.ai.realtime.gateway;

import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeProtocolCodec;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
import com.robot.platform.ai.realtime.runtime.RealtimeRuntimeOutput;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

public class AiRealtimeWebSocketHandler extends AbstractWebSocketHandler {

    private final RealtimeProtocolCodec codec;
    private final Supplier<RuntimeManager> runtimeManagerSupplier;

    public AiRealtimeWebSocketHandler(RealtimeProtocolCodec codec, RuntimeManager runtimeManager) {
        this(codec, () -> runtimeManager);
    }

    AiRealtimeWebSocketHandler(RealtimeProtocolCodec codec, Supplier<RuntimeManager> runtimeManagerSupplier) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.runtimeManagerSupplier = Objects.requireNonNull(runtimeManagerSupplier, "runtimeManagerSupplier");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        DeviceSession deviceSession = trustedDeviceSession(session);
        RuntimeManager manager = runtimeManager();
        manager.open(session.getId(), deviceSession);
        manager.attachOutput(session.getId(), new WebSocketRuntimeOutput(session, codec));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        RealtimeClientEvent event = codec.decodeClientText(message.getPayload());
        runtimeManager().require(session.getId()).acceptControl(event);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        runtimeManager().require(session.getId()).acceptAudio(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Runtime runtime = runtimeManager().remove(session.getId());
        if (runtime != null) {
            runtime.close(status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Runtime runtime = runtimeManager().remove(session.getId());
        if (runtime != null) {
            runtime.close(CloseStatus.SERVER_ERROR.withReason(
                    exception == null || exception.getMessage() == null ? "transport error" : exception.getMessage()));
        }
    }

    private RuntimeManager runtimeManager() {
        RuntimeManager manager = runtimeManagerSupplier.get();
        if (manager == null) {
            throw new IllegalStateException("Realtime Agent runtime manager is not available");
        }
        return manager;
    }

    private static DeviceSession trustedDeviceSession(WebSocketSession session) {
        Object value = session.getAttributes().get(AiRealtimeHandshakeInterceptor.DEVICE_SESSION_ATTRIBUTE);
        if (!(value instanceof DeviceSession deviceSession)
                || deviceSession.audience() != ApiAudience.DEVICE
                || deviceSession.tenantId() <= 0
                || deviceSession.deviceId() <= 0
                || deviceSession.robotId() <= 0) {
            throw new IllegalStateException("Trusted DeviceSession is missing from WebSocket handshake");
        }
        return deviceSession;
    }

    public interface RuntimeManager {
        void open(String webSocketSessionId, DeviceSession deviceSession);

        default void attachOutput(String webSocketSessionId, RealtimeRuntimeOutput output) {
        }

        Runtime require(String webSocketSessionId);

        Runtime remove(String webSocketSessionId);
    }

    public interface Runtime {
        void acceptControl(RealtimeClientEvent event);

        void acceptAudio(ByteBuffer pcm);

        void close(CloseStatus reason);
    }

    private static final class WebSocketRuntimeOutput implements RealtimeRuntimeOutput {
        private final WebSocketSession session;
        private final RealtimeProtocolCodec codec;

        private WebSocketRuntimeOutput(WebSocketSession session, RealtimeProtocolCodec codec) {
            this.session = session;
            this.codec = codec;
        }

        @Override
        public synchronized void sendEvent(RealtimeServerEvent event) {
            send(new TextMessage(codec.encodeServerEvent(event)));
        }

        @Override
        public synchronized void sendAudio(ByteBuffer audio) {
            if (audio == null) {
                throw new IllegalArgumentException("Realtime output audio must not be null");
            }
            send(new BinaryMessage(audio.asReadOnlyBuffer()));
        }

        private void send(WebSocketMessage<?> message) {
            try {
                session.sendMessage(message);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to send realtime WebSocket output", exception);
            }
        }
    }
}
