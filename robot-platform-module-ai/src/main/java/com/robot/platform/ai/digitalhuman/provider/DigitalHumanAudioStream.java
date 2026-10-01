package com.robot.platform.ai.digitalhuman.provider;

import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serial, bounded uploads keep provider callbacks off HTTP I/O and preserve playback order. */
public class DigitalHumanAudioStream implements AutoCloseable {
    private static final int SAMPLE_RATE = 24000;
    private static final int CHUNK_BYTES = SAMPLE_RATE; // 500 ms of mono PCM16
    private final DigitalHumanProvider.Session session;
    private final Consumer<String> onError;
    private final ThreadPoolExecutor worker;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean closed;
    private boolean failed;

    public DigitalHumanAudioStream(DigitalHumanProvider.Session session, Consumer<String> onError) {
        this.session = session;
        this.onError = onError;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24), r -> {
            Thread thread = new Thread(r, "digital-human-audio");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void append(ByteBuffer pcm) {
        if (closed || failed) return;
        ByteBuffer input = pcm.asReadOnlyBuffer();
        while (input.hasRemaining() && !failed && !closed) {
            byte[] part = new byte[Math.min(input.remaining(), CHUNK_BYTES - pending.size())];
            input.get(part);
            pending.writeBytes(part);
            if (pending.size() == CHUNK_BYTES) flush();
        }
    }

    public synchronized void flush() {
        if (closed || failed || pending.size() == 0) return;
        byte[] pcm = pending.toByteArray();
        pending.reset();
        if (pcm.length % 2 != 0) { fail(); return; }
        submit(() -> session.audio(wav(pcm)));
    }

    public synchronized void interrupt() {
        if (closed) return;
        pending.reset();
        worker.getQueue().clear();
        // An in-flight upload finishes before this command, so it cannot refill a flushed remote queue.
        submit(session::interrupt);
    }

    private void submit(Runnable action) {
        try {
            worker.execute(() -> {
                try { action.run(); }
                catch (RuntimeException error) { fail(); }
            });
        } catch (RejectedExecutionException error) { fail(); }
    }

    private void fail() {
        synchronized (this) {
            if (closed || failed) return;
            failed = true;
            pending.reset();
            worker.getQueue().clear();
            // Best-effort stop of anything already buffered by the renderer.
            worker.execute(() -> { try { session.interrupt(); } catch (RuntimeException ignored) {} });
        }
        onError.accept("数字人音视频暂时不可用，请重新连接");
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        pending.reset();
        worker.getQueue().clear();
        worker.execute(() -> { try { session.close(); } catch (RuntimeException ignored) {} });
        worker.shutdown();
    }

    static byte[] wav(byte[] pcm) {
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        wav.putShort((short) 1).putShort((short) 1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2);
        wav.putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return wav.array();
    }
}
