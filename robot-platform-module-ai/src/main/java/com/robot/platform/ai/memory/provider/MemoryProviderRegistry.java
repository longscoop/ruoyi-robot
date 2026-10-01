package com.robot.platform.ai.memory.provider;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.memory.service.MemorySelection;
import com.robot.platform.ai.memory.service.MemorySnippet;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
public class MemoryProviderRegistry implements DisposableBean {
    private final Map<String, MemoryProvider> providers = new HashMap<>();
    private final MemoryProviderProperties properties;
    private final ThreadPoolExecutor reads = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "memory-recall"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public MemoryProviderRegistry(List<MemoryProvider> implementations, MemoryProviderProperties properties) {
        this.properties = properties;
        for (var provider : implementations)
            if (providers.putIfAbsent(provider.name(),provider) != null) throw new IllegalStateException("Duplicate memory provider");
    }
    public MemoryProvider require(String mode) {
        MemoryProvider result = providers.get(MemoryModes.provider(mode));
        if (result == null) throw new IllegalStateException("Memory provider unavailable");
        return result;
    }
    public List<MemorySnippet> queryMemory(ConversationIdentity i, AiAgentConfig a, String q, int limit) {
        MemoryNamespace.of(i,a);
        if (!a.memoryReadEnabled() || !MemoryModes.persistent(a.memoryMode())) return List.of();
        if (new MemoryDirectiveParser().parse(q) == MemoryDirectiveParser.Directive.FORGET) return List.of();
        MemoryProvider provider = require(a.memoryMode());
        Future<List<MemorySnippet>> task = null;
        long started = System.nanoTime();
        try {
            int candidates = Math.min(100, Math.max(16, limit * 5));
            task = reads.submit(() -> {
                try {
                    TenantContextHolder.setTenantId(i.tenantId());
                    TenantContextHolder.setIgnore(false);
                    return provider.queryMemory(i, a, q, candidates);
                } finally { TenantContextHolder.clear(); }
            });
            List<MemorySnippet> rows = task.get(Math.max(50, Math.min(2000, properties.getQueryDeadlineMillis())), TimeUnit.MILLISECONDS);
            return MemorySelection.select(q, rows, Math.max(1, Math.min(limit, 100)),
                    Set.of("mem0ai", "powermem").contains(provider.name()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException error) {
            LoggerFactory.getLogger(getClass()).warn("Memory query skipped: {} tenant {} ({})", provider.name(), i.tenantId(), error.getClass().getSimpleName());
            return List.of();
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
            LoggerFactory.getLogger(getClass()).info("Memory recall timing: provider={} elapsedMs={}",
                    provider.name(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
    public void saveMemory(ConversationIdentity i, AiAgentConfig a, List<CompletedTurn> messages) {
        MemoryNamespace.of(i,a);
        if (a.memoryWriteEnabled() && MemoryModes.persistent(a.memoryMode())) require(a.memoryMode()).saveMemory(i,a,List.copyOf(messages));
    }
    public int saveMessageThreshold() { return Math.max(2,Math.min(100,properties.getSaveMessageThreshold())); }
    public int contextMaxChars() { return Math.max(256,Math.min(16000,properties.getContextMaxChars())); }
    @Override public void destroy() { reads.shutdownNow(); }
}
