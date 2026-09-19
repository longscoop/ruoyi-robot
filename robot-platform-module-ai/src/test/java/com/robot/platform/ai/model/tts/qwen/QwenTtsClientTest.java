package com.robot.platform.ai.model.tts.qwen;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.TtsRequest;
import com.robot.platform.ai.model.client.TtsStream;
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

class QwenTtsClientTest {

    @Test
    void configuresCommitModeStreamsTextAndDecodesAudio() {
        FakeConnector connector=new FakeConnector();
        List<ProviderEvent> events=new ArrayList<>();
        QwenTtsClient client=new QwenTtsClient(connector);
        ResolvedModel model=new ResolvedModel(
                11L,304L,404L,"QWEN","TTS","qwen3-tts-flash-realtime",
                "wss://dashscope.example/api-ws/v1/realtime",
                "{\"workspaceId\":\"ws-1\"}",
                "{\"voice\":\"Cherry\",\"sampleRate\":24000,\"languageType\":\"Chinese\"}",
                "secret");

        TtsStream stream=client.stream(new TtsRequest(model,"你好。"),events::add);

        assertEquals(URI.create("wss://dashscope.example/api-ws/v1/realtime?model=qwen3-tts-flash-realtime"),connector.uri);
        assertEquals("Bearer secret",connector.headers.get("Authorization"));
        connector.emit("""
                {"type":"session.created","session":{"id":"s1","model":"qwen3-tts-flash-realtime"}}
                """);
        assertEquals("session.update",connector.type(0));
        assertEquals("Cherry",connector.json(0).path("session").path("voice").asText());
        assertEquals("commit",connector.json(0).path("session").path("mode").asText());

        connector.emit("""
                {"type":"session.updated","session":{"id":"s1"}}
                """);
        assertEquals("input_text_buffer.append",connector.type(1));
        assertEquals("你好。",connector.json(1).path("text").asText());
        assertEquals("input_text_buffer.commit",connector.type(2));

        connector.emit("""
                {"type":"response.audio.delta","delta":"AQID"}
                """);
        connector.emit("""
                {"type":"response.audio.done"}
                """);

        assertInstanceOf(ProviderEvent.AudioDelta.class,events.get(0));
        ByteBuffer audio=((ProviderEvent.AudioDelta)events.get(0)).audio();
        byte[] bytes=new byte[audio.remaining()];audio.get(bytes);
        assertArrayEquals(new byte[]{1,2,3},bytes);
        assertInstanceOf(ProviderEvent.AudioDone.class,events.get(1));
        assertTrue(connector.webSocket.text.stream().anyMatch(x->x.contains("\"session.finish\"")));

        stream.cancel();
        assertTrue(connector.webSocket.closeCode>=0);
    }

    private static final class FakeConnector implements QwenTtsClient.WebSocketConnector {
        private URI uri; private Map<String,String> headers; private WebSocket.Listener listener;
        private final FakeWebSocket webSocket=new FakeWebSocket();
        @Override public CompletableFuture<WebSocket> connect(URI uri,Map<String,String> headers,WebSocket.Listener listener){
            this.uri=uri;this.headers=Map.copyOf(headers);this.listener=listener;listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }
        void emit(String json){listener.onText(webSocket,json,true);}
        com.fasterxml.jackson.databind.JsonNode json(int i){return com.robot.platform.framework.common.util.json.JsonUtils.parseTree(webSocket.text.get(i));}
        String type(int i){return json(i).path("type").asText();}
    }
    private static final class FakeWebSocket implements WebSocket {
        private final List<String> text=new ArrayList<>(); private int closeCode=-1;
        @Override public CompletableFuture<WebSocket> sendText(CharSequence d,boolean l){text.add(d.toString());return CompletableFuture.completedFuture(this);}
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer d,boolean l){throw new AssertionError("Qwen TTS uses JSON text frames");}
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
