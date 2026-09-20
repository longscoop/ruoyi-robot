package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
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
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return List.of();
        };
        AsyncMemoryPipeline pipeline = new AsyncMemoryPipeline(extractor, new MemoryDirectiveParser(), 1, 4);
        try {
            long started = System.nanoTime();
            pipeline.submit(new MemoryExtractor.CompletedTurn("记住我喜欢咖啡", "好的"),
                    new ConversationIdentity(1L, 2L, 3L, "VOICEPRINT", .9, true),
                    mock(AiAgentConfig.class));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis < 200, "submit must be non-blocking");
            assertTrue(entered.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pipeline.destroy();
        }
    }
}
