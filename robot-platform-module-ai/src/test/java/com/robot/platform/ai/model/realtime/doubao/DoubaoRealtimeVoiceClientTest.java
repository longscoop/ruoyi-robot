package com.robot.platform.ai.model.realtime.doubao;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoRealtimeVoiceClientTest {

    @Test
    void buildsTransportFromProviderConfigurationWithoutHardcodedAccountValues() {
        FakeConnector connector = new FakeConnector();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);

        RealtimeProviderSession session = client.open(model(), event -> { });

        assertNotNull(session);
        assertEquals(URI.create("wss://openspeech.example/api/v3/realtime/dialogue"), connector.uri);
        assertEquals("app-123", connector.headers.get("X-Api-App-ID"));
        assertEquals("decrypted-access-key", connector.headers.get("X-Api-Access-Key"));
        assertEquals("volc.speech.dialog.custom", connector.headers.get("X-Api-Resource-Id"));
        assertEquals("app-key-custom", connector.headers.get("X-Api-App-Key"));
        assertNotNull(connector.headers.get("X-Api-Connect-Id"));
        assertFalse(connector.headers.get("X-Api-Connect-Id").isBlank());
    }

    @Test
    void lifecycleWaitsForConnectionAndSessionAckThenStreamsAudio() {
        FakeConnector connector = new FakeConnector();
        List<ProviderEvent> received = new ArrayList<>();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);
        RealtimeProviderSession session = client.open(model(), received::add);

        assertEquals(List.of(1), connector.sentEvents());

        session.appendAudio(ByteBuffer.wrap(new byte[]{1, 2, 3}));
        session.speechStopped();
        assertEquals(List.of(1), connector.sentEvents());

        connector.emit(serverJson(50, "server-session", "{}"));
        assertEquals(List.of(1, 100), connector.sentEvents());

        connector.emit(serverJson(150, "server-session", "{}"));
        assertEquals(List.of(1, 100, 200, 400), connector.sentEvents());

        connector.emit(serverJson(451, "server-session", "{\"results\":[{\"text\":\"你好\"}]}"));
        connector.emit(serverJson(550, "server-session", "{\"content\":\"我在\"}"));
        connector.emit(serverBinary(352, "server-session", new byte[]{4, 5, 6}));
        connector.emit(serverJson(359, "server-session", "{}"));

        assertEquals(4, received.size());
        assertInstanceOf(ProviderEvent.TranscriptDone.class, received.get(0));
        assertInstanceOf(ProviderEvent.TextDelta.class, received.get(1));
        assertInstanceOf(ProviderEvent.AudioDelta.class, received.get(2));
        assertInstanceOf(ProviderEvent.AudioDone.class, received.get(3));
    }

    @Test
    void cancelSuppressesOldOutputUntilNextSpeechTurnAndCloseFinishesProtocol() {
        FakeConnector connector = new FakeConnector();
        List<ProviderEvent> received = new ArrayList<>();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);
        RealtimeProviderSession session = client.open(model(), received::add);
        connector.emit(serverJson(50, "server-session", "{}"));
        connector.emit(serverJson(150, "server-session", "{}"));

        session.cancelCurrentResponse();
        assertTrue(connector.sentEvents().contains(515));
        connector.emit(serverBinary(352, "server-session", new byte[]{9}));
        assertTrue(received.isEmpty());

        session.speechStarted();
        session.speechStopped();
        connector.emit(serverJson(550, "server-session", "{\"content\":\"新一轮\"}"));
        assertEquals(1, received.size());

        session.close();
        assertEquals(List.of(1, 100, 515, 400, 102, 2), connector.sentEvents().stream()
                .filter(event -> event != 200)
                .toList());
        assertEquals(1000, connector.webSocket.closeCode);
    }


    @Test
    void turnAwareCallbacksKeepLatePreviousTurnBoundUntilItsAsrFinal() {
        FakeConnector connector = new FakeConnector();
        List<TurnEvent> received = new ArrayList<>();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);
        RealtimeProviderSession session = client.openTurnAware(
                model(), (turnId, generation, event) -> received.add(new TurnEvent(turnId, generation, event)));

        connector.emit(serverJson(50, "server-session", "{}"));
        connector.emit(serverJson(150, "server-session", "{}"));

        session.beginTurn("turn-a", 1L);
        session.speechStarted();
        session.speechStopped();

        session.beginTurn("turn-b", 2L);
        session.speechStarted();
        session.speechStopped();

        connector.emit(serverJson(451, "server-session",
                "{\"results\":[{\"text\":\"A输入\",\"is_interim\":false}]}"));
        connector.emit(serverJson(550, "server-session", "{\"content\":\"A回复\"}"));
        connector.emit(serverJson(559, "server-session", "{}"));

        connector.emit(serverJson(451, "server-session",
                "{\"results\":[{\"text\":\"B输入\",\"is_interim\":false}]}"));
        connector.emit(serverJson(550, "server-session", "{\"content\":\"B回复\"}"));

        connector.emit(serverBinary(352, "server-session", new byte[]{7}));
        connector.emit(serverJson(359, "server-session", "{}"));
        connector.emit(serverJson(559, "server-session", "{}"));
        connector.emit(serverBinary(352, "server-session", new byte[]{8}));

        assertEquals(9, received.size());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(0).stamp());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(1).stamp());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(2).stamp());
        assertEquals(new TurnStamp("turn-b", 2L), received.get(3).stamp());
        assertEquals(new TurnStamp("turn-b", 2L), received.get(4).stamp());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(5).stamp());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(6).stamp());
        assertEquals(new TurnStamp("turn-b", 2L), received.get(7).stamp());
        assertEquals(new TurnStamp("turn-b", 2L), received.get(8).stamp());
    }

    @Test
    void apiKeyAuthModeUsesOnlyApiKeyAndResourceId() {
        FakeConnector connector = new FakeConnector();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);
        ResolvedModel model = new ResolvedModel(11L, 302L, 401L,
                "DOUBAO", "REALTIME_S2S", "doubao-s2s",
                "wss://openspeech.example/api/v3/realtime/dialogue",
                "{\"authType\":\"API_KEY\",\"resourceId\":\"volc.speech.dialog-v2\"}",
                "{\"speaker\":\"voice\"}", "decrypted-api-key");

        client.open(model, event -> { });

        assertEquals("decrypted-api-key", connector.headers.get("X-Api-Key"));
        assertEquals("volc.speech.dialog-v2", connector.headers.get("X-Api-Resource-Id"));
        assertFalse(connector.headers.containsKey("X-Api-App-ID"));
        assertFalse(connector.headers.containsKey("X-Api-Access-Key"));
        assertFalse(connector.headers.containsKey("X-Api-App-Key"));
    }

    @Test
    void rejectsMissingProviderConfigurationOrWrongModel() {
        FakeConnector connector = new FakeConnector();
        DoubaoRealtimeVoiceClient client = new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);

        ResolvedModel wrong = new ResolvedModel(11L, 302L, 401L, "QWEN", "REALTIME_S2S",
                "m", "wss://example", "{}", "{}", "secret");
        assertThrows(IllegalArgumentException.class, () -> client.open(wrong, event -> { }));

        ResolvedModel missingConfig = new ResolvedModel(11L, 302L, 401L, "DOUBAO", "REALTIME_S2S",
                "m", "wss://example", "{}", "{}", "secret");
        assertThrows(IllegalArgumentException.class, () -> client.open(missingConfig, event -> { }));
    }


    private record TurnEvent(String turnId, long generation, ProviderEvent event) {
        private TurnStamp stamp() {
            return new TurnStamp(turnId, generation);
        }
    }

    private record TurnStamp(String turnId, long generation) {
    }

    private static ResolvedModel model() {
        return new ResolvedModel(11L, 302L, 401L,
                "DOUBAO", "REALTIME_S2S", "doubao-s2s",
                "wss://openspeech.example/api/v3/realtime/dialogue",
                "{\"authType\":\"APP_ACCESS_KEY\",\"appId\":\"app-123\",\"resourceId\":\"volc.speech.dialog.custom\",\"appKey\":\"app-key-custom\"}",
                "{\"speaker\":\"zh_female_vv_jupiter_bigtts\",\"botName\":\"小优\",\"speakingStyle\":\"简洁自然\",\"outputSampleRate\":24000,\"recvTimeout\":120,\"inputMod\":\"audio\",\"model\":\"1.2.1.1\"}",
                "decrypted-access-key")
                .withRealtimeSession("你是家庭机器人小优。", null);
    }

    private static ByteBuffer serverJson(int event, String sessionId, String json) {
        return TestFrames.serverJson(event, sessionId, json);
    }

    private static ByteBuffer serverBinary(int event, String sessionId, byte[] payload) {
        return TestFrames.serverBinary(event, sessionId, payload);
    }

    private static final class FakeConnector implements DoubaoRealtimeVoiceClient.WebSocketConnector {
        private URI uri;
        private Map<String, String> headers;
        private WebSocket.Listener listener;
        private final FakeWebSocket webSocket = new FakeWebSocket();

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers, WebSocket.Listener listener) {
            this.uri = uri;
            this.headers = Map.copyOf(headers);
            this.listener = listener;
            listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }

        void emit(ByteBuffer frame) {
            listener.onBinary(webSocket, frame, true);
        }

        List<Integer> sentEvents() {
            return webSocket.binary.stream().map(TestFrames::event).toList();
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<ByteBuffer> binary = new ArrayList<>();
        private int closeCode = -1;

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            throw new AssertionError("Doubao realtime protocol must stay binary");
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            ByteBuffer copy = ByteBuffer.allocate(data.remaining());
            copy.put(data.duplicate()).flip();
            binary.add(copy);
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closeCode = statusCode;
            return CompletableFuture.completedFuture(this);
        }
        @Override public void request(long n) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return closeCode >= 0; }
        @Override public boolean isInputClosed() { return closeCode >= 0; }
        @Override public void abort() { closeCode = 1006; }
    }

    static final class TestFrames {
        static int event(ByteBuffer frame) {
            return frame.duplicate().getInt(4);
        }

        static ByteBuffer serverJson(int event, String sessionId, String json) {
            return DoubaoRealtimeCodecTestHelper.serverFrame(event, sessionId,
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        }

        static ByteBuffer serverBinary(int event, String sessionId, byte[] payload) {
            return DoubaoRealtimeCodecTestHelper.serverFrame(event, sessionId, payload, false);
        }
    }
}
