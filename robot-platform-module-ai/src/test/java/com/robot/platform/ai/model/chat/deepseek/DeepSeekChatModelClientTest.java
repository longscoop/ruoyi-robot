package com.robot.platform.ai.model.chat.deepseek;

import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekChatModelClientTest {

    @Test
    void postsStreamingChatRequestAndPreservesCancelableHandle() {
        FakeTransport transport = new FakeTransport(sse("""
                data: {"choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}}

                data: [DONE]

                """));
        DeepSeekChatModelClient client =
                new DeepSeekChatModelClient(new DeepSeekSseDecoder(), transport, Runnable::run);
        List<ProviderEvent> events = new ArrayList<>();
        ResolvedModel model = new ResolvedModel(
                11L, 301L, 401L, "DEEPSEEK", "CHAT", "deepseek-flash",
                "https://api.deepseek.example", "{}", "{}", "secret-key");
        ChatRequest request = new ChatRequest(model,
                List.of(
                        new ChatRequest.ChatMessage("system", "You are Xiaoyou."),
                        new ChatRequest.ChatMessage("user", "你好")),
                List.of(new ChatRequest.ChatTool(
                        "inspect_home", "Inspect home", "{\"type\":\"object\"}")));

        ChatStream stream = client.stream(request, events::add);

        assertEquals(URI.create("https://api.deepseek.example/chat/completions"), transport.uri);
        assertEquals("Bearer secret-key", transport.headers.get("Authorization"));
        assertEquals("application/json", transport.headers.get("Content-Type"));

        var body = JsonUtils.parseTree(transport.body);
        assertEquals("deepseek-flash", body.path("model").asText());
        assertTrue(body.path("stream").asBoolean());
        assertTrue(body.path("stream_options").path("include_usage").asBoolean());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("你好", body.path("messages").get(1).path("content").asText());
        assertEquals("inspect_home",
                body.path("tools").get(0).path("function").path("name").asText());

        assertTrue(events.stream().anyMatch(ProviderEvent.TextDelta.class::isInstance));
        assertTrue(events.stream().anyMatch(ProviderEvent.Usage.class::isInstance));
        assertTrue(events.stream().anyMatch(ProviderEvent.TextDone.class::isInstance));

        stream.cancel();
        assertEquals(1, transport.cancelCount);
    }

    @Test
    void rejectsWrongProviderModelTypeOrMissingCredential() {
        DeepSeekChatModelClient client =
                new DeepSeekChatModelClient(new DeepSeekSseDecoder(),
                        new FakeTransport(sse("data: [DONE]\\n\\n")), Runnable::run);

        ResolvedModel wrongProvider = new ResolvedModel(
                11L, 301L, 401L, "QWEN", "CHAT", "m",
                "https://example", "{}", "{}", "secret");
        assertThrows(IllegalArgumentException.class,
                () -> client.stream(new ChatRequest(wrongProvider, List.of()), event -> {}));

        ResolvedModel wrongType = new ResolvedModel(
                11L, 301L, 401L, "DEEPSEEK", "ASR", "m",
                "https://example", "{}", "{}", "secret");
        assertThrows(IllegalArgumentException.class,
                () -> client.stream(new ChatRequest(wrongType, List.of()), event -> {}));

        ResolvedModel missingCredential = new ResolvedModel(
                11L, 301L, 401L, "DEEPSEEK", "CHAT", "m",
                "https://example", "{}", "{}", null);
        assertThrows(IllegalArgumentException.class,
                () -> client.stream(new ChatRequest(missingCredential, List.of()), event -> {}));
    }

    private static ByteArrayInputStream sse(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FakeTransport implements DeepSeekChatModelClient.Transport {
        private final ByteArrayInputStream response;
        private URI uri;
        private Map<String, String> headers;
        private String body;
        private int cancelCount;

        private FakeTransport(ByteArrayInputStream response) {
            this.response = response;
        }

        @Override
        public DeepSeekChatModelClient.TransportCall post(
                URI uri, Map<String, String> headers, String body) {
            this.uri = uri;
            this.headers = Map.copyOf(headers);
            this.body = body;
            return new DeepSeekChatModelClient.TransportCall() {
                @Override
                public CompletableFuture<DeepSeekChatModelClient.TransportResponse> future() {
                    return CompletableFuture.completedFuture(
                            new DeepSeekChatModelClient.TransportResponse(200, response));
                }

                @Override
                public void cancel() {
                    cancelCount++;
                }
            };
        }
    }
}
