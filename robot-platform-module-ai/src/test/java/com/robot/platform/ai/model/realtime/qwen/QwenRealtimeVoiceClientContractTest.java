package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.AbstractRealtimeVoiceClientContractTest;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class QwenRealtimeVoiceClientContractTest extends AbstractRealtimeVoiceClientContractTest {

    @Override
    protected Harness createHarness() {
        return new QwenHarness();
    }

    private static final class QwenHarness implements Harness {
        private final FakeConnector connector = new FakeConnector();
        private final QwenRealtimeVoiceClient client =
                new QwenRealtimeVoiceClient(new QwenRealtimeCodec(), connector);

        @Override
        public RealtimeProviderSession open(RealtimeProviderListener listener) {
            ResolvedModel model = new ResolvedModel(11L, 101L, 201L,
                    "QWEN", "REALTIME_S2S", "qwen-test",
                    "wss://qwen.example/realtime", "{}", "{}", "secret")
                    .withRealtimeSession("prompt", "{\"voice\":\"Cherry\"}");
            return client.open(model, listener);
        }

        @Override
        public void ready() {
        }

        @Override
        public void emitTranscriptTextAudio() {
            connector.listener.onText(connector.webSocket,
                    "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"你好\"}", true);
            connector.listener.onText(connector.webSocket,
                    "{\"type\":\"response.audio_transcript.delta\",\"delta\":\"我在\"}", true);
            connector.listener.onText(connector.webSocket,
                    "{\"type\":\"response.audio.delta\",\"delta\":\"AQID\"}", true);
            connector.listener.onText(connector.webSocket,
                    "{\"type\":\"response.audio.done\"}", true);
        }

        @Override
        public void assertAudioForwarded(byte[] expected) {
            String append = connector.webSocket.sentText.stream()
                    .filter(value -> "input_audio_buffer.append".equals(
                            JsonUtils.parseTree(value).path("type").asText()))
                    .findFirst().orElseThrow();
            assertArrayEquals(expected, Base64.getDecoder().decode(
                    JsonUtils.parseTree(append).path("audio").asText()));
        }

        @Override
        public void assertClosed() {
            assertEquals(1000, connector.webSocket.closeCode);
        }
    }

    private static final class FakeConnector implements QwenRealtimeVoiceClient.WebSocketConnector {
        private WebSocket.Listener listener;
        private final FakeWebSocket webSocket = new FakeWebSocket();

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, String authorization, WebSocket.Listener listener) {
            this.listener = listener;
            listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> sentText = new ArrayList<>();
        private int closeCode = -1;

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sentText.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            throw new AssertionError("Qwen adapter must not use binary WebSocket messages");
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
}
