package com.robot.platform.ai.digitalhuman.service;

import com.robot.platform.ai.digitalhuman.provider.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Preview handles are private to an authenticated user and tenant, never upstream session IDs. */
@Service
public class DigitalHumanPreviewSessions {
    private final DigitalHumanProviders providers;
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "digital-human-preview-cleanup"); t.setDaemon(true); return t;
    });

    public DigitalHumanPreviewSessions(DigitalHumanProviders providers) {
        this.providers = providers;
        cleaner.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
    }

    public synchronized Answer open(long tenant, long user, long human, String config, String sdp) {
        if (sessions.size() >= 32 || sessions.values().stream().filter(e -> e.tenant == tenant && e.user == user).count() >= 2)
            throw invalidParamException("预览连接数已达上限，请关闭已有预览后重试");
        DigitalHumanProvider.Session session = providers.open(config, sdp);
        String handle = UUID.randomUUID().toString();
        sessions.put(handle, new Entry(tenant, user, human, session));
        return new Answer(handle, "answer", session.answerSdp());
    }

    public void speak(long tenant, long user, long human, String handle, String text) {
        if (text == null || text.isBlank() || text.length() > 2000) throw invalidParamException("请输入 1–2000 字的试听文本");
        require(tenant, user, human, handle).session.speak(text.trim());
    }

    public void interrupt(long tenant, long user, long human, String handle) { require(tenant, user, human, handle).session.interrupt(); }
    public boolean speaking(long tenant, long user, long human, String handle) { return require(tenant, user, human, handle).session.isSpeaking(); }
    public void close(long tenant, long user, long human, String handle) {
        Entry entry = require(tenant, user, human, handle);
        if (sessions.remove(handle, entry)) release(entry);
    }

    private Entry require(long tenant, long user, long human, String handle) {
        Entry entry = sessions.get(handle);
        if (entry == null || entry.tenant != tenant || entry.user != user || entry.human != human
                || entry.lastUsed.isBefore(Instant.now().minusSeconds(120)))
            throw invalidParamException("数字人预览连接不存在或已过期");
        entry.lastUsed = Instant.now();
        return entry;
    }

    private void expire() {
        sessions.forEach((handle, entry) -> {
            if (entry.lastUsed.isBefore(Instant.now().minusSeconds(120)) && sessions.remove(handle, entry)) release(entry);
        });
    }

    private static void release(Entry entry) { try { entry.session.close(); } catch (RuntimeException ignored) {} }
    @PreDestroy public void destroy() { cleaner.shutdownNow(); sessions.values().forEach(DigitalHumanPreviewSessions::release); sessions.clear(); }
    public record Answer(String sessionId, String type, String sdp) {}
    private static class Entry {
        final long tenant, user, human;
        final DigitalHumanProvider.Session session;
        volatile Instant lastUsed = Instant.now();
        Entry(long tenant, long user, long human, DigitalHumanProvider.Session session) {
            this.tenant = tenant; this.user = user; this.human = human; this.session = session;
        }
    }
}
