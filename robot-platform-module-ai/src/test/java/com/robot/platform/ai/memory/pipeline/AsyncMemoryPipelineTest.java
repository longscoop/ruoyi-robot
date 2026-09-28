package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AsyncMemoryPipelineTest {
    @Test
    void submitReturnsWithoutWaitingForBlockedExtractor() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MemoryExtractor extractor = (turn, identity, directive) -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return List.of();
        };
        AsyncMemoryPipeline pipeline = new AsyncMemoryPipeline(extractor, new MemoryDirectiveParser(), 1, 4);
        AiAgentConfig agent = mock(AiAgentConfig.class);
        var caller = Executors.newSingleThreadExecutor();
        try {
            var submission = caller.submit(() -> pipeline.submit(
                    new MemoryExtractor.CompletedTurn("记住我喜欢咖啡", "好的"),
                    new ConversationIdentity(1L, 2L, 3L, "VOICEPRINT", .9, true), agent));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> submission.get(3, TimeUnit.SECONDS),
                    "submit must return while the extractor is blocked");
            assertEquals(1L, release.getCount());
        } finally {
            release.countDown();
            caller.shutdownNow();
            pipeline.destroy();
        }
    }
}
