package com.robot.platform.ai.model.chat.platform;

import com.robot.platform.ai.model.client.ChatRequest;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlatformChatModelClientTest {
    private static ResolvedModel model(String provider, String base, String code) {
        return new ResolvedModel(1, 2, 3, provider, "CHAT", code, base, "{}", "{}", "key");
    }

    private static ChatRequest request(ResolvedModel model) {
        return new ChatRequest(model, List.of(new ChatRequest.ChatMessage("system", "Be concise"),
                new ChatRequest.ChatMessage("user", "Where is the robot?")));
    }

    @Test
    void endpointsAndBodiesMatchFourPlatformContracts() {
        HttpClient http = HttpClient.newHttpClient();
        var qwen = new PlatformChatModelClient("QWEN", http);
        var fastGpt = new PlatformChatModelClient("FASTGPT", http);
        var dify = new PlatformChatModelClient("DIFY", http);
        var coze = new PlatformChatModelClient("COZE", http);
        assertEquals(URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                qwen.endpoint(model("QWEN", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus")));
        assertEquals(URI.create("https://fastgpt.example/api/v1/chat/completions"),
                fastGpt.endpoint(model("FASTGPT", "https://fastgpt.example/api/v1", "app-1")));
        assertEquals(URI.create("https://api.dify.ai/v1/chat-messages"),
                dify.endpoint(model("DIFY", "https://api.dify.ai/v1", "app")));
        assertEquals(URI.create("https://api.coze.cn/v3/chat"),
                coze.endpoint(model("COZE", "https://api.coze.cn", "bot-1")));

        var qwenBody = JsonUtils.parseTree(qwen.body(request(model("QWEN", "https://qwen.example/v1", "qwen-plus"))));
        assertEquals("qwen-plus", qwenBody.path("model").asText());
        assertEquals(2, qwenBody.path("messages").size());
        var fastBody = JsonUtils.parseTree(fastGpt.body(request(model("FASTGPT", "https://fastgpt.example/api/v1", "app-1"))));
        assertEquals("app-1", fastBody.path("appId").asText());
        assertFalse(fastBody.has("chatId"));
        var difyBody = JsonUtils.parseTree(dify.body(request(model("DIFY", "https://dify.example/v1", "app"))));
        assertEquals("streaming", difyBody.path("response_mode").asText());
        assertTrue(difyBody.path("query").asText().contains("Where is the robot?"));
        assertFalse(difyBody.has("conversation_id"));
        var cozeBody = JsonUtils.parseTree(coze.body(request(model("COZE", "https://coze.example", "bot-1"))));
        assertEquals("bot-1", cozeBody.path("bot_id").asText());
        assertFalse(cozeBody.path("auto_save_history").asBoolean());
        assertEquals("user", cozeBody.path("additional_messages").get(0).path("role").asText());
    }

    @Test
    void decodesProviderSpecificStreamsWithoutExecutingWorkflowEvents() {
        assertEvents("QWEN", "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\ndata: [DONE]\n\n");
        assertEvents("FASTGPT", "event: flowNodeStatus\ndata: {\"status\":\"running\"}\n\n"
                + "event: answer\ndata: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                + "event: answer\ndata: [DONE]\n\n");
        assertEvents("DIFY", "data: {\"event\":\"message\",\"answer\":\"你好\"}\n\n"
                + "data: {\"event\":\"message_end\"}\n\n");
        assertEvents("COZE", "event: conversation.message.delta\ndata: {\"type\":\"answer\",\"content\":\"你好\"}\n\n"
                + "event: conversation.message.completed\ndata: {\"type\":\"answer\",\"content\":\"你好\"}\n\n"
                + "event: conversation.chat.completed\ndata: {\"usage\":{\"input_tokens\":2,\"output_tokens\":1}}\n\n");
    }

    private static void assertEvents(String type, String sse) {
        var decoder = new PlatformSseDecoder(type);
        List<ProviderEvent> events = new ArrayList<>();
        for (String line : sse.split("\n", -1)) events.addAll(decoder.accept(line));
        events.addAll(decoder.finish());
        assertEquals(1, events.stream().filter(ProviderEvent.TextDelta.class::isInstance).count(), type);
        assertEquals(1, events.stream().filter(ProviderEvent.TextDone.class::isInstance).count(), type);
        assertTrue(events.stream().noneMatch(ProviderEvent.ProviderError.class::isInstance), type);
        assertTrue(events.stream().noneMatch(ProviderEvent.ToolCall.class::isInstance), type);
    }

    @Test
    void failedOrTruncatedStreamCannotBeReportedAsCompleted() {
        var decoder = new PlatformSseDecoder("COZE");
        decoder.accept("event: conversation.chat.failed");
        List<ProviderEvent> failed = decoder.accept("data: {\"code\":1}");
        assertTrue(failed.isEmpty());
        failed = decoder.accept("");
        assertEquals(1, failed.stream().filter(ProviderEvent.ProviderError.class::isInstance).count());
        assertTrue(decoder.finish().isEmpty());
        var truncated = new PlatformSseDecoder("DIFY");
        assertEquals(1, truncated.finish().stream().filter(ProviderEvent.ProviderError.class::isInstance).count());
    }

    @Test
    void sendsAuthenticatedHttpRequestAndDeliversStreamEvents() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: [DONE]\n\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new PlatformChatModelClient("QWEN", HttpClient.newHttpClient());
            var events = new CopyOnWriteArrayList<ProviderEvent>();
            CountDownLatch complete = new CountDownLatch(1);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            client.stream(request(model("QWEN", url, "qwen-plus")), event -> {
                events.add(event);
                if (event instanceof ProviderEvent.TextDone) complete.countDown();
            });
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            assertEquals("Bearer key", authorization.get());
            assertTrue(events.stream().anyMatch(ProviderEvent.TextDelta.class::isInstance));
        } finally {
            server.stop(0);
        }
    }
}
