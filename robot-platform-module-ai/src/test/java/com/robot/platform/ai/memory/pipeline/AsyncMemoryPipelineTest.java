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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.ai.memory.extract.MemoryCandidate;

class AsyncMemoryPipelineTest {
    private static AiAgentConfig agent(boolean enabled) {
        return new AiAgentConfig(1, 1, "a", "system", 1, 1, "NATIVE", 2L, 3L, null, null,
                "LONG_TERM", enabled, enabled);
    }

    @Test
    void disabledAndDoNotRememberNeverExtractOrWrite() {
        MemoryExtractor extractor = mock(MemoryExtractor.class);
        MemoryCandidateWriter writer = mock(MemoryCandidateWriter.class);
        var pipeline = new AsyncMemoryPipeline(extractor, new MemoryDirectiveParser(), writer);
        try {
            pipeline.submit(new MemoryExtractor.CompletedTurn("记住演示口令", "好"),
                    ConversationIdentity.anonymous(1, 2), agent(false));
            pipeline.submit(new MemoryExtractor.CompletedTurn("不要记住演示口令", "好"),
                    ConversationIdentity.anonymous(1, 2), agent(true));
            verifyNoInteractions(extractor, writer);
        } finally { pipeline.destroy(); }
    }

    @Test
    void extractedCandidatesAreWrittenWithTenantAndConversationProvenance() throws Exception {
        var candidate = new MemoryCandidate("ROBOT", "FACT", "演示口令是蓝色小船", .9, .95, null);
        CountDownLatch written = new CountDownLatch(1);
        MemoryExtractor extractor = (turn, identity, directive) -> {
            assertEquals(1, TenantContextHolder.getRequiredTenantId());
            return List.of(candidate);
        };
        MemoryCandidateWriter writer = mock(MemoryCandidateWriter.class);
        doAnswer(call -> { assertEquals(1, TenantContextHolder.getRequiredTenantId()); written.countDown(); return null; })
                .when(writer).write(anyList(), any(), any(), any());
        var pipeline = new AsyncMemoryPipeline(extractor, new MemoryDirectiveParser(), writer);
        var turn = new MemoryExtractor.CompletedTurn("请记住演示口令是蓝色小船", "好的", 91L);
        var identity = ConversationIdentity.anonymous(1, 2);
        try {
            pipeline.submit(turn, identity, agent(true));
            assertTrue(written.await(3, TimeUnit.SECONDS));
            verify(writer).write(List.of(candidate), identity, MemoryDirectiveParser.Directive.REMEMBER, turn);
            assertNull(TenantContextHolder.getTenantId());
        } finally { pipeline.destroy(); }
    }

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
        AiAgentConfig agent = agent(true);
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
