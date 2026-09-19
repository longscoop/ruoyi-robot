package com.robot.platform.ai.realtime.gateway;

import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeProtocolCodec;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRealtimeWebSocketHandlerTest {

    @Mock private RealtimeProtocolCodec codec;
    @Mock private AiRealtimeWebSocketHandler.RuntimeManager runtimeManager;
    @Mock private AiRealtimeWebSocketHandler.Runtime runtime;
    @Mock private WebSocketSession webSocketSession;

    @Test
    void connectionUsesTrustedDeviceSessionFromHandshakeAttributes() throws Exception {
        DeviceSession trusted = trustedSession();
        when(webSocketSession.getId()).thenReturn("ws-1");
        when(webSocketSession.getAttributes()).thenReturn(attributes(trusted));
        var handler = new AiRealtimeWebSocketHandler(codec, runtimeManager);

        handler.afterConnectionEstablished(webSocketSession);

        verify(runtimeManager).open("ws-1", trusted);
    }

    @Test
    void textFrameIsDecodedByPlatformCodecAndPassedToRuntime() throws Exception {
        var event = new RealtimeClientEvent.SpeechStartedEvent("evt-1");
        when(webSocketSession.getId()).thenReturn("ws-1");
        when(runtimeManager.require("ws-1")).thenReturn(runtime);
        when(codec.decodeClientText("{\"type\":\"input.speech_started\"}")).thenReturn(event);
        var handler = new AiRealtimeWebSocketHandler(codec, runtimeManager);

        handler.handleMessage(webSocketSession, new TextMessage("{\"type\":\"input.speech_started\"}"));

        verify(codec).decodeClientText("{\"type\":\"input.speech_started\"}");
        verify(runtime).acceptControl(event);
    }

    @Test
    void binaryFrameIsPassedAsByteBufferWithoutJsonConversion() throws Exception {
        when(webSocketSession.getId()).thenReturn("ws-1");
        when(runtimeManager.require("ws-1")).thenReturn(runtime);
        var handler = new AiRealtimeWebSocketHandler(codec, runtimeManager);
        byte[] pcm = new byte[]{1, 2, 3, 4};

        handler.handleMessage(webSocketSession, new BinaryMessage(pcm));

        ArgumentCaptor<ByteBuffer> captor = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(runtime).acceptAudio(captor.capture());
        ByteBuffer actual = captor.getValue().duplicate();
        byte[] bytes = new byte[actual.remaining()];
        actual.get(bytes);
        assertArrayEquals(pcm, bytes);
        verifyNoInteractions(codec);
    }

    @Test
    void connectionCloseRemovesRuntimeAndClosesItExactlyOnce() throws Exception {
        when(webSocketSession.getId()).thenReturn("ws-1");
        when(runtimeManager.remove("ws-1")).thenReturn(runtime).thenReturn(null);
        var handler = new AiRealtimeWebSocketHandler(codec, runtimeManager);
        CloseStatus status = CloseStatus.NORMAL.withReason("client close");

        handler.afterConnectionClosed(webSocketSession, status);
        handler.afterConnectionClosed(webSocketSession, status);

        verify(runtimeManager, times(2)).remove("ws-1");
        verify(runtime, times(1)).close(status);
    }

    @Test
    void connectionWithoutTrustedHandshakeIdentityIsRejectedBeforeRuntimeOpen() {
        when(webSocketSession.getAttributes()).thenReturn(Map.of());
        var handler = new AiRealtimeWebSocketHandler(codec, runtimeManager);

        assertThrows(IllegalStateException.class, () -> handler.afterConnectionEstablished(webSocketSession));

        verify(runtimeManager, never()).open(anyString(), any());
    }

    private static DeviceSession trustedSession() {
        return new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
    }

    private static Map<String, Object> attributes(DeviceSession session) {
        Map<String, Object> values = new HashMap<>();
        values.put(AiRealtimeHandshakeInterceptor.DEVICE_SESSION_ATTRIBUTE, session);
        return values;
    }
}
