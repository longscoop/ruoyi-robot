package com.robot.platform.ai.digitalhuman.service;

import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class DigitalHumanDialogueSessions {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DigitalHumanDialogueSessions.class);
    private final DigitalHumanDialogueFactory factory;
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "digital-human-dialogue-cleanup"); thread.setDaemon(true); return thread;
    });

    public DigitalHumanDialogueSessions(DigitalHumanDialogueFactory factory) {
        this.factory = factory;
        cleaner.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
    }

    public String open(long tenant, long user, long human) {
        String id = UUID.randomUUID().toString();
        Entry entry = new Entry(tenant, user, human);
        synchronized (sessions) {
            if (sessions.size() >= 32 || sessions.values().stream().filter(e -> e.tenant == tenant && e.user == user).count() >= 2)
                throw invalidParamException("测试对话连接数已达上限，请先结束已有对话");
            sessions.put(id, entry);
        }
        try {
            RealtimeProviderSession provider = factory.open(tenant, human, (turn, version, event) -> entry.event(version, event));
            boolean attached;
            synchronized (entry) {
                attached = !entry.closed;
                if (attached) entry.provider = provider;
                if (attached && entry.openError) throw invalidParamException("语音模型连接失败，请检查智能体配置");
            }
            if (!attached) {
                provider.close();
                throw invalidParamException("测试对话已关闭");
            }
            return id;
        } catch (RuntimeException error) {
            sessions.remove(id, entry);
            entry.close();
            throw invalidParamException("语音模型连接失败，请检查智能体模型、音色和服务凭据");
        }
    }

    public SseEmitter turn(long tenant, long user, long human, String id, String base64Pcm) {
        Entry entry = require(tenant, user, human, id);
        // At most 30 seconds, PCM16 mono 16 kHz; reject oversized input before allocating decoded bytes.
        if (base64Pcm == null || base64Pcm.length() > 1_280_000) throw invalidParamException("录音最长 30 秒");
        final byte[] pcm;
        try { pcm = Base64.getDecoder().decode(base64Pcm); }
        catch (IllegalArgumentException error) { throw invalidParamException("录音格式无效"); }
        if (pcm.length < 3200 || pcm.length > 960000 || pcm.length % 2 != 0)
            throw invalidParamException("请录制 0.1–30 秒的单声道 16 kHz PCM16 音频");
        return entry.turn(pcm);
    }

    public void interrupt(long tenant, long user, long human, String id) { require(tenant, user, human, id).interrupt(); }
    public void touch(long tenant, long user, long human, String id) { require(tenant, user, human, id); }
    public void close(long tenant, long user, long human, String id) {
        Entry entry = require(tenant, user, human, id);
        if (sessions.remove(id, entry)) entry.close();
    }

    private Entry require(long tenant, long user, long human, String id) {
        Entry entry = sessions.get(id);
        if (entry == null || entry.tenant != tenant || entry.user != user || entry.human != human
                || entry.closed || entry.lastUsed.isBefore(Instant.now().minusSeconds(180)))
            throw invalidParamException("测试对话不存在或已过期，请重新开始");
        entry.lastUsed = Instant.now();
        return entry;
    }
    private void expire() {
        sessions.forEach((id, entry) -> {
            if (entry.lastUsed.isBefore(Instant.now().minusSeconds(180)) && sessions.remove(id, entry)) entry.close();
        });
    }
    @PreDestroy public void destroy() { cleaner.shutdownNow(); sessions.values().forEach(Entry::close); sessions.clear(); }

    private static final class Entry {
        final long tenant, user, human;
        final ThreadPoolExecutor commands = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), r -> { Thread t = new Thread(r, "digital-human-dialogue"); t.setDaemon(true); return t; });
        volatile Instant lastUsed = Instant.now();
        volatile boolean closed;
        boolean openError;
        long generation;
        RealtimeProviderSession provider;
        SseEmitter emitter;

        Entry(long tenant, long user, long human) { this.tenant = tenant; this.user = user; this.human = human; }

        synchronized SseEmitter turn(byte[] pcm) {
            if (closed || openError || provider == null) throw invalidParamException("测试对话不可用，请重新开始");
            if (emitter != null) throw invalidParamException("正在回答，请先打断或等待回答完成");
            long version = ++generation;
            SseEmitter stream = new SseEmitter(120_000L);
            emitter = stream;
            stream.onCompletion(() -> cancelIfCurrent(version));
            stream.onError(error -> cancelIfCurrent(version));
            stream.onTimeout(() -> timeout(version));
            send(stream, Map.of("type", "thinking", "turnId", String.valueOf(version)));
            try {
                commands.execute(() -> {
                    String phase = "voice_start";
                    try {
                        if (!current(version)) return;
                        provider.beginTurn(String.valueOf(version), version);
                        provider.speechStarted();
                        phase = "audio_upload";
                        for (int offset = 0; offset < pcm.length && current(version); offset += 6400)
                            provider.appendAudio(ByteBuffer.wrap(pcm, offset, Math.min(6400, pcm.length - offset)));
                        phase = "audio_commit";
                        if (current(version)) provider.speechStopped();
                    } catch (RuntimeException error) {
                        LOG.warn("Digital human voice input failed: human={} turn={} phase={} pcmBytes={} exception={}",
                                human, version, phase, pcm.length, error.getClass().getSimpleName());
                        String code = "Qwen ASR pending audio buffer is full".equals(error.getMessage())
                                ? "asr_audio_buffer_full" : phase + "_failed";
                        event(version, new ProviderEvent.ProviderError(code, "", true));
                    }
                });
            } catch (RejectedExecutionException error) {
                emitter = null;
                stream.complete();
                throw invalidParamException("测试对话繁忙，请稍后重试");
            }
            return stream;
        }

        synchronized boolean current(long version) { return !closed && emitter != null && generation == version; }

        synchronized void timeout(long version) {
            if (!current(version)) return;
            event(version, new ProviderEvent.ProviderError("timeout", "", true));
            cancelProvider();
        }

        synchronized void event(long version, ProviderEvent event) {
            if (closed) return;
            if (event instanceof ProviderEvent.ProviderError && version <= 0) {
                openError = true;
                if (emitter != null) event(generation, event);
                return;
            }
            if (!current(version)) return;
            lastUsed = Instant.now();
            SseEmitter stream = emitter;
            Map<String, Object> message = new LinkedHashMap<>();
            if (event instanceof ProviderEvent.TranscriptDelta value) { message.put("type", "user.delta"); message.put("text", value.text()); }
            else if (event instanceof ProviderEvent.TranscriptDone value) { message.put("type", "user.done"); message.put("text", value.text()); }
            else if (event instanceof ProviderEvent.TextDelta value) { message.put("type", "assistant.delta"); message.put("text", value.text()); }
            else if (event instanceof ProviderEvent.TextDone value) { message.put("type", "assistant.text"); message.put("text", value.text()); }
            else if (event instanceof ProviderEvent.AudioDelta value) {
                ByteBuffer audio = value.audio().duplicate();
                byte[] bytes = new byte[audio.remaining()]; audio.get(bytes);
                message.put("type", "audio"); message.put("pcm", Base64.getEncoder().encodeToString(bytes)); message.put("sampleRate", 24000);
            } else if (event instanceof ProviderEvent.AudioDone) message.put("type", "done");
            else if (event instanceof ProviderEvent.ProviderError error) {
                message.put("type", "error");
                // Only expose known local categories, never raw upstream messages or credentials.
                String description = switch (error.code() == null ? "" : error.code()) {
                    case "asr_audio_buffer_full" -> "语音识别缓冲区已满，请缩短录音后重试";
                    case "voice_start_failed" -> "语音输入初始化失败，请检查识别模型配置后重试";
                    case "audio_upload_failed", "audio_commit_failed" -> "录音提交到语音服务失败，请重新开始对话";
                    case "asr_connect_failed" -> "语音识别服务连接失败，请检查网络和识别模型配置";
                    case "asr_connect_timeout" -> "语音识别连接超时，请检查后端网络与代理配置";
                    case "asr_auth_failed" -> "语音识别服务鉴权失败，请检查服务凭据与访问权限";
                    case "tts_connect_failed", "tts_open_failed", "tts_input_failed", "tts_input_full" -> "语音合成失败，请检查音色与合成模型配置";
                    case "cascade_start_failed" -> "对话模型调用失败，请检查智能体的对话模型配置";
                    case "timeout" -> "语音服务响应超时，请重新开始对话";
                    default -> "语音服务返回错误，请检查服务凭据、模型和音色后重试";
                };
                LOG.warn("Digital human dialogue failed: human={} turn={} reason={}", human, version, description);
                message.put("message", description);
            }
            else return;
            message.put("turnId", String.valueOf(version));
            boolean complete = event instanceof ProviderEvent.AudioDone || event instanceof ProviderEvent.ProviderError;
            if (complete) emitter = null;
            send(stream, message);
            if (complete) stream.complete();
        }

        synchronized void cancelIfCurrent(long version) { if (current(version)) interrupt(); }
        synchronized void interrupt() {
            // Audio may still be playing in the browser after the provider has finished.
            // Repeated cancellation must not erase a previously queued cancel command.
            if (emitter == null) return;
            generation++;
            SseEmitter stream = emitter; emitter = null;
            if (stream != null) { send(stream, Map.of("type", "interrupted")); stream.complete(); }
            commands.getQueue().clear();
            cancelProvider();
        }
        private void cancelProvider() {
            try { commands.execute(() -> { try { if (provider != null) provider.cancelCurrentResponse(); } catch (RuntimeException ignored) {} }); }
            catch (RejectedExecutionException ignored) { }
        }
        synchronized void close() {
            if (closed) return;
            closed = true; generation++;
            SseEmitter stream = emitter; emitter = null;
            if (stream != null) stream.complete();
            commands.getQueue().clear();
            commands.execute(() -> { try { if (provider != null) provider.close(); } catch (RuntimeException ignored) {} });
            commands.shutdown();
        }
        private void send(SseEmitter stream, Map<String, Object> message) {
            try { stream.send(SseEmitter.event().data(message)); }
            catch (Exception error) {
                if (emitter == stream) { emitter = null; generation++; cancelProvider(); }
                stream.completeWithError(error);
            }
        }
    }
}
