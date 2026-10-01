package com.robot.platform.ai.model.asr.qwen;

import com.robot.platform.ai.model.client.AsrSession;
import com.robot.platform.ai.model.client.ResolvedModel;
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

class QwenAsrClientTest {

    @Test void acceptsThirtySecondRecordingBeforeTaskStartedAndFlushesInOrder() {
        FakeConnector connector = new FakeConnector();
        var model = new ResolvedModel(1, 2, 3, "QWEN", "ASR", "paraformer-realtime-v2",
                "wss://example.test", "{}", "{}", "test");
        var session = new QwenAsrClient(connector).open(model, event -> {});
        session.speechStarted();
        for (int i = 0; i < 150; i++) {
            byte[] chunk = new byte[6400]; chunk[0] = (byte) i;
            session.appendAudio(ByteBuffer.wrap(chunk));
        }
        session.speechStopped();
        assertTrue(connector.webSocket.binary.isEmpty());
        connector.emitText("{\"header\":{\"event\":\"task-started\"}}");
        assertEquals(150, connector.webSocket.binary.size());
        for (int i = 0; i < 150; i++) assertEquals((byte) i, connector.webSocket.binary.get(i).get(0));
        assertEquals("finish-task", connector.action(connector.webSocket.text.size() - 1));
        session.cancel();
    }

    @Test
    void usesRunTaskBinaryAudioFinishTaskAndNormalizesResults() {
        FakeConnector connector = new FakeConnector();
        List<ProviderEvent> events = new ArrayList<>();
        QwenAsrClient client = new QwenAsrClient(connector);
        ResolvedModel model = new ResolvedModel(
                11L, 303L, 403L, "QWEN", "ASR", "fun-asr-realtime",
                "wss://dashscope.example/api-ws/v1/inference",
                "{\"workspaceId\":\"ws-1\"}",
                "{\"sampleRate\":16000,\"languageHints\":[\"zh\"]}",
                "secret");

        AsrSession session = client.open(model, events::add);

        assertEquals(URI.create("wss://dashscope.example/api-ws/v1/inference"), connector.uri);
        assertEquals("Bearer secret", connector.headers.get("Authorization"));
        assertEquals("ws-1", connector.headers.get("X-DashScope-WorkSpace"));
        assertEquals("run-task", connector.action(0));
        assertEquals("fun-asr-realtime", connector.json(0).path("payload").path("model").asText());
        assertEquals(16000, connector.json(0).path("payload").path("parameters").path("sample_rate").asInt());

        session.appendAudio(ByteBuffer.wrap(new byte[]{1,2,3}));
        assertTrue(connector.webSocket.binary.isEmpty());

        connector.emitText("""
                {"header":{"task_id":"t","event":"task-started","attributes":{}},"payload":{}}
                """);
        assertEquals(1, connector.webSocket.binary.size());

        connector.emitText("""
                {"header":{"task_id":"t","event":"result-generated","attributes":{}},"payload":{"output":{"sentence":{"text":"你","heartbeat":false,"sentence_end":false}}}}
                """);
        connector.emitText("""
                {"header":{"task_id":"t","event":"result-generated","attributes":{}},"payload":{"output":{"sentence":{"text":"你好","heartbeat":false,"sentence_end":true}}}}
                """);
        assertInstanceOf(ProviderEvent.TranscriptDelta.class, events.get(0));
        assertEquals("你", ((ProviderEvent.TranscriptDelta)events.get(0)).text());
        assertInstanceOf(ProviderEvent.TranscriptDelta.class, events.get(1));
        assertEquals("你好", ((ProviderEvent.TranscriptDelta)events.get(1)).text());
        assertFalse(events.stream().anyMatch(ProviderEvent.TranscriptDone.class::isInstance));

        session.speechStopped();
        assertEquals("finish-task", connector.action(connector.webSocket.text.size()-1));

        connector.emitText("""
                {"header":{"task_id":"t","event":"task-finished","attributes":{}},"payload":{}}
                """);
        assertEquals(WebSocket.NORMAL_CLOSURE, connector.webSocket.closeCode);
        assertEquals("你好", ((ProviderEvent.TranscriptDone) events.get(events.size()-1)).text());
        assertEquals(1,events.stream().filter(ProviderEvent.TranscriptDone.class::isInstance).count());
    }


    @Test
    void boundsAudioQueuedBeforeTaskStarted() {
        FakeConnector connector = new FakeConnector();
        QwenAsrClient client = new QwenAsrClient(connector);
        ResolvedModel model = new ResolvedModel(
                11L, 303L, 403L, "QWEN", "ASR", "fun-asr-realtime",
                "wss://dashscope.example/api-ws/v1/inference",
                "{}", "{\"sampleRate\":16000}", "secret");

        AsrSession session = client.open(model, event -> { });
        byte[] halfLimit = new byte[15 * 16000 * 2];

        session.appendAudio(ByteBuffer.wrap(halfLimit));
        session.appendAudio(ByteBuffer.wrap(halfLimit));

        assertThrows(IllegalStateException.class,
                () -> session.appendAudio(ByteBuffer.wrap(new byte[]{1})));
        assertTrue(connector.webSocket.binary.isEmpty());
    }

    @Test void pendingConnectionDoesNotBlockAudioCaptureAndCancelAbortsLateSocket() {
        var pending = new CompletableFuture<WebSocket>();
        var model = new ResolvedModel(1,2,3,"QWEN","ASR","asr","wss://example.test","{}","{}","test");
        var events = new ArrayList<ProviderEvent>();
        var client = new QwenAsrClient((uri, headers, listener) -> pending);
        AsrSession session = assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> client.open(model, events::add));
        session.speechStarted(); session.appendAudio(ByteBuffer.wrap(new byte[640])); session.speechStopped();
        session.cancel();
        var socket = new FakeWebSocket(); pending.complete(socket);
        assertTrue(socket.isOutputClosed()); assertTrue(events.isEmpty());
    }

    @Test void connectionTimeoutIsClassifiedWithoutLeakingUpstreamDetails() {
        var pending = new CompletableFuture<WebSocket>();
        var events = new ArrayList<ProviderEvent>();
        var client = new QwenAsrClient((uri, headers, listener) -> pending);
        var model = new ResolvedModel(1,2,3,"QWEN","ASR","asr","wss://example.test","{}","{}","test");
        client.open(model, events::add);
        pending.completeExceptionally(new java.net.http.HttpConnectTimeoutException("upstream details must not leak"));
        var error = (ProviderEvent.ProviderError) events.get(0);
        assertEquals("asr_connect_timeout", error.code());
        assertFalse(error.message().contains("upstream details"));
    }

    private static final class FakeConnector implements QwenAsrClient.WebSocketConnector {
        private URI uri;
        private Map<String,String> headers;
        private WebSocket.Listener listener;
        private final FakeWebSocket webSocket=new FakeWebSocket();

        @Override public CompletableFuture<WebSocket> connect(URI uri, Map<String,String> headers, WebSocket.Listener listener) {
            this.uri=uri; this.headers=Map.copyOf(headers); this.listener=listener;
            listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }
        void emitText(String json){ listener.onText(webSocket,json,true); }
        com.fasterxml.jackson.databind.JsonNode json(int index){
            return com.robot.platform.framework.common.util.json.JsonUtils.parseTree(webSocket.text.get(index));
        }
        String action(int index){ return json(index).path("header").path("action").asText(); }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> text=new ArrayList<>();
        private final List<ByteBuffer> binary=new ArrayList<>();
        private int closeCode=-1;
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last){text.add(data.toString());return CompletableFuture.completedFuture(this);}
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last){ByteBuffer copy=ByteBuffer.allocate(data.remaining());copy.put(data.duplicate()).flip();binary.add(copy);return CompletableFuture.completedFuture(this);}
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer m){return CompletableFuture.completedFuture(this);}
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer m){return CompletableFuture.completedFuture(this);}
        @Override public CompletableFuture<WebSocket> sendClose(int c,String r){closeCode=c;return CompletableFuture.completedFuture(this);}
        @Override public void request(long n){}
        @Override public String getSubprotocol(){return "";}
        @Override public boolean isOutputClosed(){return closeCode>=0;}
        @Override public boolean isInputClosed(){return closeCode>=0;}
        @Override public void abort(){closeCode=1006;}
    }
}
