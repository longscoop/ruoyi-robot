package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class QwenRealtimeVoiceClientTest {

    @Test
    void buildsConnectionFromResolvedModelAndSendsSessionUpdateOnOpen() {
        FakeConnector connector = new FakeConnector();
        QwenRealtimeVoiceClient client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);
        ResolvedModel model = model("qwen-custom-realtime", "secret-key")
                .withRealtimeSession("You are Xiaoyou.", "{\"voice\":\"Cherry\"}");

        RealtimeProviderSession session = client.open(model, event -> { });

        assertNotNull(session);
        assertEquals(URI.create("wss://qwen.example/api-ws/v1/realtime?model=qwen-custom-realtime"),
                connector.uri);
        assertEquals("Bearer secret-key", connector.authorization);
        assertEquals(1, connector.webSocket.sentText.size());
        var update = JsonUtils.parseTree(connector.webSocket.sentText.get(0));
        assertEquals("session.update", update.path("type").asText());
        assertEquals("You are Xiaoyou.", update.path("session").path("instructions").asText());
        assertEquals("Cherry", update.path("session").path("voice").asText());
    }

    @Test
    void sessionMapsPlatformOperationsToQwenManualProtocol() {
        FakeConnector connector = new FakeConnector();
        QwenRealtimeVoiceClient client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);
        RealtimeProviderSession session = client.open(
                model("qwen3.5-omni-plus-realtime", "secret-key")
                        .withRealtimeSession("prompt", "{\"voice\":\"Cherry\"}"),
                event -> { });
        connector.webSocket.sentText.clear();

        session.speechStarted();
        session.appendAudio(ByteBuffer.wrap(new byte[]{1, 2, 3}));
        session.speechStopped();
        session.cancelCurrentResponse();
        session.close();

        List<String> types = connector.webSocket.sentText.stream()
                .map(JsonUtils::parseTree)
                .map(node -> node.path("type").asText())
                .toList();
        assertEquals(List.of(
                "input_audio_buffer.clear",
                "input_audio_buffer.append",
                "input_audio_buffer.commit",
                "response.create",
                "response.cancel"), types);
        assertEquals(1000, connector.webSocket.closeCode);
    }

    @Test
    void websocketServerMessagesBecomeNormalizedProviderEvents() {
        FakeConnector connector = new FakeConnector();
        List<ProviderEvent> received = new ArrayList<>();
        QwenRealtimeVoiceClient client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);
        client.open(model("qwen-model-from-db", "secret-key")
                        .withRealtimeSession("prompt", null),
                received::add);

        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.audio_transcript.delta\",\"delta\":\"你好\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.audio.delta\",\"delta\":\"AQID\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.audio.done\"}", true);

        assertEquals(3, received.size());
        assertInstanceOf(ProviderEvent.TextDelta.class, received.get(0));
        assertInstanceOf(ProviderEvent.AudioDelta.class, received.get(1));
        assertInstanceOf(ProviderEvent.AudioDone.class, received.get(2));
    }

    @Test
    void rejectsWrongProviderModelTypeOrMissingCredential() {
        FakeConnector connector = new FakeConnector();
        QwenRealtimeVoiceClient client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);

        ResolvedModel wrongProvider = new ResolvedModel(11L, 101L, 201L,
                "DOUBAO", "REALTIME_S2S", "m", "wss://qwen.example/realtime", null, null, "secret");
        assertThrows(IllegalArgumentException.class,
                () -> client.open(wrongProvider, event -> { }));

        ResolvedModel wrongType = new ResolvedModel(11L, 101L, 201L,
                "QWEN", "CHAT", "m", "wss://qwen.example/realtime", null, null, "secret");
        assertThrows(IllegalArgumentException.class,
                () -> client.open(wrongType, event -> { }));

        assertThrows(IllegalArgumentException.class,
                () -> client.open(model("m", null), event -> { }));
    }

    private static ResolvedModel model(String modelCode, String credential) {
        return new ResolvedModel(11L, 101L, 201L,
                "QWEN", "REALTIME_S2S", modelCode,
                "wss://qwen.example/api-ws/v1/realtime",
                "{}", "{}", credential);
    }

    private static final class FakeConnector implements QwenRealtimeVoiceClient.WebSocketConnector {
        private URI uri;
        private String authorization;
        private WebSocket.Listener listener;
        private final FakeWebSocket webSocket = new FakeWebSocket();

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, String authorization, WebSocket.Listener listener) {
            this.uri = uri;
            this.authorization = authorization;
            this.listener = listener;
            listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> sentText = new ArrayList<>();
        private int closeCode = -1;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sentText.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            throw new AssertionError("Qwen JSON protocol should not use WebSocket binary frames");
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closeCode = statusCode;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return closeCode >= 0;
        }

        @Override
        public boolean isInputClosed() {
            return closeCode >= 0;
        }

        @Override
        public void abort() {
            closeCode = 1006;
        }
    }
}
