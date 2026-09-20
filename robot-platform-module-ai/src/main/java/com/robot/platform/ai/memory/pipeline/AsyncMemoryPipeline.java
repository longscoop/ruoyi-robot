package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AsyncMemoryPipeline implements MemoryPipeline, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(AsyncMemoryPipeline.class);
    private final MemoryExtractor extractor;
    private final MemoryDirectiveParser directiveParser;
    private final ThreadPoolExecutor executor;
    private final AtomicLong rejectedTasks = new AtomicLong();

    public AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser) {
        this(extractor, directiveParser, 2, 100);
    }

    AsyncMemoryPipeline(MemoryExtractor extractor, MemoryDirectiveParser directiveParser, int threads, int queueCapacity) {
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

    @Override
    public void submit(MemoryExtractor.CompletedTurn turn, ConversationIdentity identity, AiAgentConfig agent) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(agent, "agent");
        MemoryDirectiveParser.Directive directive = directiveParser.parse(turn.userText());
        executor.execute(() -> {
            try { extractor.extract(turn, identity, directive); }
            catch (RuntimeException error) { log.warn("Memory extraction failed", error); }
        });
    }

    long rejectedTasks() { return rejectedTasks.get(); }

    @Override public void destroy() { executor.shutdownNow(); }
}
