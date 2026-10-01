package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class QwenRealtimeVoiceClientTest {

    @Test
    void springCreatesClientWithCodec() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(com.robot.platform.ai.model.config.AiModelNetworkConfiguration.class,
                    QwenRealtimeCodec.class, QwenRealtimeVoiceClient.class);
            context.refresh();
            assertEquals("QWEN", context.getBean(QwenRealtimeVoiceClient.class).providerType());
        }
    }

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
    void turnAwareCallbacksKeepProviderResponseBoundToOriginalGeneration() {
        FakeConnector connector = new FakeConnector();
        List<TurnEvent> received = new ArrayList<>();
        QwenRealtimeVoiceClient client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);
        RealtimeProviderSession session = client.openTurnAware(
                model("qwen-model-from-db", "secret-key").withRealtimeSession("prompt", null),
                (turnId, generation, event) -> received.add(new TurnEvent(turnId, generation, event)));

        session.beginTurn("turn-a", 1L);
        session.speechStarted();
        session.speechStopped();
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"item-a\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"resp-a\"}}", true);

        session.beginTurn("turn-b", 2L);
        session.speechStarted();
        session.speechStopped();
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"item-b\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"resp-b\"}}", true);

        connector.listener.onText(connector.webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.delta\",\"item_id\":\"item-a\",\"text\":\"旧\",\"stash\":\"\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.audio.delta\",\"response_id\":\"resp-a\",\"delta\":\"AQ==\"}", true);
        connector.listener.onText(connector.webSocket,
                "{\"type\":\"response.audio.delta\",\"response_id\":\"resp-b\",\"delta\":\"Ag==\"}", true);

        assertEquals(3, received.size());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(0).stamp());
        assertEquals(new TurnStamp("turn-a", 1L), received.get(1).stamp());
        assertEquals(new TurnStamp("turn-b", 2L), received.get(2).stamp());
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

    @Test
    void closeAndInterruptionDoNotWaitForAnInFlightRuntimeCallback() throws Exception {
        FakeConnector connector = new FakeConnector();
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        RealtimeProviderSession session = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector)
                .openTurnAware(model("qwen-test", "secret-key"), (turn, generation, event) -> {
                    callbackEntered.countDown();
                    try {
                        assertTrue(releaseCallback.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                });
        session.beginTurn("turn-1", 1);
        CompletableFuture<Void> callback = CompletableFuture.runAsync(() -> connector.listener.onText(
                connector.webSocket, "{\"type\":\"response.audio.delta\",\"delta\":\"AQ==\"}", true));
        try {
            assertTrue(callbackEntered.await(1, TimeUnit.SECONDS));
            CompletableFuture.runAsync(() -> {
                session.cancelCurrentResponse();
                session.close();
            }).get(1, TimeUnit.SECONDS);
            assertEquals(1000, connector.webSocket.closeCode);
        } finally {
            releaseCallback.countDown();
            callback.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeDoesNotBlockOnProviderNetworkCompletion() throws Exception {
        FakeConnector connector = new FakeConnector();
        RealtimeProviderSession session = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector)
                .open(model("qwen-test", "secret-key"), event -> { });
        connector.webSocket.closeFuture = new CompletableFuture<>();
        try {
            CompletableFuture.runAsync(session::close).get(1, TimeUnit.SECONDS);
            assertFalse(connector.webSocket.closeFuture.isDone());
            assertThrows(IllegalStateException.class, session::speechStarted);
        } finally {
            connector.webSocket.closeFuture.complete(connector.webSocket);
        }
    }


    @Test
    void preparesRelevantInstructionsBeforeResponseAndSkipsEmptyOrCancelledAsr() {
        FakeConnector connector = new FakeConnector();
        var client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);
        List<String> queries = new ArrayList<>();
        var session = client.openTurnAware(model("qwen-test", "secret-key")
                .withRealtimeSession("role", "{\"voice\":\"Cherry\",\"input_audio_transcription\":{\"model\":\"gummy-realtime-v1\"}}"),
                (turn, generation, event) -> { }, text -> { queries.add(text); return "role plus " + text; });
        connector.webSocket.sentText.clear();
        session.beginTurn("turn-1", 1);
        session.speechStopped();
        assertEquals(1, connector.webSocket.sentText.size()); // Commit only; inference waits for ASR.
        connector.listener.onText(connector.webSocket, "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"item-1\"}", true);
        connector.listener.onText(connector.webSocket, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"item-1\",\"transcript\":\"小黑\"}", true);
        assertEquals(List.of("小黑"), queries);
        var update = JsonUtils.parseTree(connector.webSocket.sentText.get(1));
        assertEquals("role plus 小黑", update.path("session").path("instructions").asText());
        assertEquals("Cherry", update.path("session").path("voice").asText());
        assertEquals(2, connector.webSocket.sentText.size());
        connector.listener.onText(connector.webSocket, "{\"type\":\"session.updated\"}", true);
        assertEquals("response.create", JsonUtils.parseTree(connector.webSocket.sentText.get(2)).path("type").asText());
        session.beginTurn("turn-2", 2);
        session.speechStopped();
        connector.listener.onText(connector.webSocket, "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"item-2\"}", true);
        connector.listener.onText(connector.webSocket, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"item-2\",\"transcript\":\" ，。 \"}", true);
        assertEquals(4, connector.webSocket.sentText.size());
        session.beginTurn("turn-3", 3);
        session.speechStopped();
        session.cancelCurrentResponse();
        connector.listener.onText(connector.webSocket, "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"item-3\"}", true);
        connector.listener.onText(connector.webSocket, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"item-3\",\"transcript\":\"旧问题\"}", true);
        assertEquals(5, connector.webSocket.sentText.size());
        assertEquals(List.of("小黑"), queries);
    }

    @Test
    void stalledAsrFallsBackWithinBoundAndNeverCarriesPreviousTopicMemory() throws Exception {
        FakeConnector connector = new FakeConnector();
        var session = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector).openTurnAware(
                model("qwen-test", "secret-key").withRealtimeSession("neutral role", "{\"input_audio_transcription\":{\"model\":\"gummy-realtime-v1\"}}"),
                (turn, generation, event) -> { }, text -> "topic memory");
        session.beginTurn("turn-1", 1);
        session.speechStopped();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (connector.webSocket.sentText.size() < 3 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(3, connector.webSocket.sentText.size());
        var fallback = JsonUtils.parseTree(connector.webSocket.sentText.get(2));
        assertEquals("neutral role", fallback.path("session").path("instructions").asText());
        connector.listener.onText(connector.webSocket, "{\"type\":\"session.updated\"}", true);
        assertEquals("response.create", JsonUtils.parseTree(connector.webSocket.sentText.get(3)).path("type").asText());
        connector.listener.onText(connector.webSocket, "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"late\"}", true);
        assertEquals(4, connector.webSocket.sentText.size()); // One response per turn, including late ASR.
        session.close();
    }

    @Test
    void providerHandshakeCannotBlockSessionCreationIndefinitely() {
        QwenRealtimeVoiceClient.WebSocketConnector stalled = (uri, authorization, listener) -> new CompletableFuture<>();
        var client = new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), stalled, java.time.Duration.ofMillis(30));
        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> client.open(model("qwen-test", "secret-key"), event -> { }));
        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure.getCause());
    }

    private record TurnEvent(String turnId, long generation, ProviderEvent event) {
        private TurnStamp stamp() {
            return new TurnStamp(turnId, generation);
        }
    }

    private record TurnStamp(String turnId, long generation) {
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
        private final List<String> sentText = new java.util.concurrent.CopyOnWriteArrayList<>();
        private int closeCode = -1;
        private CompletableFuture<WebSocket> closeFuture;

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
            return closeFuture == null ? CompletableFuture.completedFuture(this) : closeFuture;
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
