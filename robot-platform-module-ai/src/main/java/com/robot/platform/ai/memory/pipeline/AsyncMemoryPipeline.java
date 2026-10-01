package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AsyncMemoryPipeline implements MemoryPipeline, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(AsyncMemoryPipeline.class);
    private final MemoryCandidateWriter writer;
    private com.robot.platform.ai.memory.provider.MemoryProviderRegistry providers;
    private final MemoryExtractor extractor;
    private final MemoryDirectiveParser directiveParser;
    private final ThreadPoolExecutor executor;
    private final AtomicLong rejectedTasks = new AtomicLong();

    @Autowired
    public AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser, MemoryCandidateWriter writer,
                               com.robot.platform.ai.memory.provider.MemoryProviderRegistry providers) {
        this(extractor, directiveParser, writer, 1, 100);
        this.providers = providers;
    }

    public AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser, MemoryCandidateWriter writer) {
        this(extractor, directiveParser, writer, 2, 100);
    }

    AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser, int threads, int queueCapacity) {
        this(extractor, directiveParser, null, threads, queueCapacity);
    }

    AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser, MemoryCandidateWriter writer,
                        int threads, int queueCapacity) {
        this.writer = writer;
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.directiveParser = Objects.requireNonNull(directiveParser, "directiveParser");
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicLong sequence = new AtomicLong();
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "ai-memory-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, (task, pool) -> {
                    rejectedTasks.incrementAndGet();
                    log.warn("Memory extraction task rejected: queue is full");
                });
    }

    @Override public int messageThreshold(AiAgentConfig agent) { return providers == null ? 6 : providers.saveMessageThreshold(); }

    @Override
    public void submit(MemoryExtractor.CompletedTurn turn, ConversationIdentity identity, AiAgentConfig agent) {
        submitBatch(java.util.List.of(Objects.requireNonNull(turn)), identity, agent);
    }

    @Override
    public void submitBatch(java.util.List<MemoryExtractor.CompletedTurn> turns, ConversationIdentity identity, AiAgentConfig agent) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(agent, "agent");
        if (identity.tenantId() != agent.tenantId()) throw new IllegalArgumentException("Memory tenant mismatch");
        if (!agent.memoryWriteEnabled() || !com.robot.platform.ai.memory.provider.MemoryModes.persistent(agent.memoryMode())) return;
        var snapshot = turns.stream().filter(t -> t != null && t.userText() != null && !t.userText().isBlank())
                .filter(t -> directiveParser.parse(t.userText()) != MemoryDirectiveParser.Directive.DO_NOT_REMEMBER).toList();
        if (snapshot.isEmpty()) return;
        executor.execute(() -> {
            try {
                TenantContextHolder.setTenantId(identity.tenantId());
                TenantContextHolder.setIgnore(false);
                if (providers != null) providers.saveMemory(identity, agent, snapshot);
                else for (var turn : snapshot) {
                    var directive = directiveParser.parse(turn.userText());
                    var candidates = extractor.extract(turn, identity, directive, agent);
                    if (writer != null) writer.write(candidates, identity, directive, turn);
                }
                log.info("Memory batch saved: tenant {} Agent {}, {} messages", identity.tenantId(), agent.agentId(), snapshot.size() * 2);
            } catch (RuntimeException error) {
                log.warn("Memory processing failed for tenant {} Agent {} ({})", identity.tenantId(),
                        agent.agentId(), error.getClass().getSimpleName());
            } finally { TenantContextHolder.clear(); }
        });
    }

    long rejectedTasks() { return rejectedTasks.get(); }

    @Override public void destroy() { executor.shutdown();
        try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException error) { executor.shutdownNow(); Thread.currentThread().interrupt(); } }
}
