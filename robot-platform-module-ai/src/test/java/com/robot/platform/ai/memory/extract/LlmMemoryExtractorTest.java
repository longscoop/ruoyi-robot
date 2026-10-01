package com.robot.platform.ai.memory.extract;

import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmMemoryExtractorTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void waitsForAsynchronousTextCompletionBeforeParsingAndClosesStream(boolean emptyDone) throws Exception {
        ChatModelClient client = mock(ChatModelClient.class);
        ChatStream stream = mock(ChatStream.class);
        var started = new CompletableFuture<ChatListener>();
        when(client.stream(any(), any())).thenAnswer(call -> {
            ChatRequest request = call.getArgument(0);
            assertEquals("USER: 记住充电桩在客厅", request.messages().get(1).content());
            started.complete(call.getArgument(1));
            return stream;
        });
        var model = new ResolvedModel(1, 2, 3, "QWEN", "CHAT", "test", "http://localhost", "{}", "{}", "test");
        var extractor = new LlmMemoryExtractor(client, model, "json", new ObjectMapper(), null);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> extractor.extract(new MemoryExtractor.CompletedTurn("记住充电桩在客厅", "好"),
                    ConversationIdentity.anonymous(1, 2), MemoryDirectiveParser.Directive.REMEMBER));
            ChatListener listener = started.get(2, TimeUnit.SECONDS);
            assertFalse(result.isDone());
            String json = """
                    {"candidates":[{"scope":"ROBOT","memoryType":"FACT","content":"充电桩在客厅",
                    "importance":0.8,"confidence":0.95,"expiresAt":null}]}
                    """;
            if (emptyDone) listener.onEvent(new ProviderEvent.TextDelta(json));
            listener.onEvent(new ProviderEvent.TextDone(emptyDone ? "" : json));
            assertEquals("充电桩在客厅", result.get(2, TimeUnit.SECONDS).get(0).content());
            verify(stream).close();
        } finally { executor.shutdownNow(); }
    }
}
