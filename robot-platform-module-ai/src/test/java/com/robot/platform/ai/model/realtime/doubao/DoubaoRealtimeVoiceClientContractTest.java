package com.robot.platform.ai.model.realtime.doubao;

import com.robot.platform.ai.model.client.AbstractRealtimeVoiceClientContractTest;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoRealtimeVoiceClientContractTest extends AbstractRealtimeVoiceClientContractTest {

    @Override
    protected Harness createHarness() {
        return new DoubaoHarness();
    }

    private static final class DoubaoHarness implements Harness {
        private final FakeConnector connector = new FakeConnector();
        private final DoubaoRealtimeVoiceClient client =
                new DoubaoRealtimeVoiceClient(new DoubaoRealtimeCodec(), connector);

        @Override
        public RealtimeProviderSession open(RealtimeProviderListener listener) {
            ResolvedModel model = new ResolvedModel(11L, 302L, 401L,
                    "DOUBAO", "REALTIME_S2S", "doubao-test",
                    "wss://doubao.example/realtime",
                    "{\"appId\":\"app\",\"resourceId\":\"resource\",\"appKey\":\"app-key\"}",
                    "{\"speaker\":\"voice\",\"botName\":\"小优\",\"outputSampleRate\":24000}",
                    "secret").withRealtimeSession("prompt", null);
            return client.open(model, listener);
        }

        @Override
        public void ready() {
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    50, "sess-contract", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    150, "sess-contract", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
        }

        @Override
        public void emitTranscriptTextAudio() {
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    451, "sess-contract",
                    "{\"results\":[{\"text\":\"你好\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    550, "sess-contract",
                    "{\"content\":\"我在\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    352, "sess-contract", new byte[]{9, 8, 7}, false));
            connector.emit(DoubaoRealtimeCodecTestHelper.serverFrame(
                    359, "sess-contract", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), true));
        }

        @Override
        public void assertAudioForwarded(byte[] expected) {
            ByteBuffer audioFrame = connector.webSocket.binary.stream()
                    .filter(frame -> frame.duplicate().getInt(4) == 200)
                    .findFirst().orElseThrow();
            byte[] frame = bytes(audioFrame);
            int sidLength = ByteBuffer.wrap(frame, 8, 4).getInt();
            int payloadOffset = 8 + 4 + sidLength;
            int payloadLength = ByteBuffer.wrap(frame, payloadOffset, 4).getInt();
            byte[] compressed = java.util.Arrays.copyOfRange(
                    frame, payloadOffset + 4, payloadOffset + 4 + payloadLength);
            try (var gzip = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(compressed))) {
                assertArrayEquals(expected, gzip.readAllBytes());
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public void assertClosed() {
            assertEquals(1000, connector.webSocket.closeCode);
        }

        private static byte[] bytes(ByteBuffer source) {
            ByteBuffer copy = source.duplicate();
            byte[] result = new byte[copy.remaining()];
            copy.get(result);
            return result;
        }
    }

    private static final class FakeConnector implements DoubaoRealtimeVoiceClient.WebSocketConnector {
        private WebSocket.Listener listener;
        private final FakeWebSocket webSocket = new FakeWebSocket();

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers, WebSocket.Listener listener) {
            this.listener = listener;
            listener.onOpen(webSocket);
            return CompletableFuture.completedFuture(webSocket);
        }

        void emit(ByteBuffer frame) {
            listener.onBinary(webSocket, frame, true);
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<ByteBuffer> binary = new ArrayList<>();
        private int closeCode = -1;

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            throw new AssertionError("Doubao adapter must not use text WebSocket messages");
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
}
