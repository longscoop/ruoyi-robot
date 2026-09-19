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
        assertInstanceOf(ProviderEvent.TranscriptDone.class, events.get(1));
        assertEquals("你好", ((ProviderEvent.TranscriptDone)events.get(1)).text());

        session.speechStopped();
        assertEquals("finish-task", connector.action(connector.webSocket.text.size()-1));

        connector.emitText("""
                {"header":{"task_id":"t","event":"task-finished","attributes":{}},"payload":{}}
                """);
        assertEquals(WebSocket.NORMAL_CLOSURE, connector.webSocket.closeCode);
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
        byte[] halfLimit = new byte[128 * 1024];

        session.appendAudio(ByteBuffer.wrap(halfLimit));
        session.appendAudio(ByteBuffer.wrap(halfLimit));

        assertThrows(IllegalStateException.class,
                () -> session.appendAudio(ByteBuffer.wrap(new byte[]{1})));
        assertTrue(connector.webSocket.binary.isEmpty());
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
