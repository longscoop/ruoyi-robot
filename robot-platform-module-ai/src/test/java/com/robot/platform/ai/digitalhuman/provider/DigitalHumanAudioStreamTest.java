package com.robot.platform.ai.digitalhuman.provider;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalHumanAudioStreamTest {
    @Test void uploadsBoundedWavChunksAndFlushesTailWithCorrectPcmFormat() {
        var session = mock(DigitalHumanProvider.Session.class);
        try (var stream = new DigitalHumanAudioStream(session, message -> fail(message))) {
            ByteBuffer input = ByteBuffer.wrap(new byte[24004]);
            stream.append(input);
            assertEquals(0, input.position());
            stream.flush();
            ArgumentCaptor<byte[]> audio = ArgumentCaptor.forClass(byte[].class);
            verify(session, timeout(2000).times(2)).audio(audio.capture());
            assertEquals(24044, audio.getAllValues().get(0).length);
            assertEquals(48, audio.getAllValues().get(1).length);
            ByteBuffer wav = ByteBuffer.wrap(audio.getAllValues().get(0)).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(24000, wav.getInt(24));
            assertEquals(1, wav.getShort(22));
            assertEquals(16, wav.getShort(34));
            assertEquals(24000, wav.getInt(40));
        }
    }

    @Test void interruptionDropsQueuedOldAudioAndRunsAfterInflightUpload() throws Exception {
        var session = mock(DigitalHumanProvider.Session.class);
        CountDownLatch uploading = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { uploading.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return null; }).when(session).audio(any());
        try (var stream = new DigitalHumanAudioStream(session, message -> fail(message))) {
            stream.append(ByteBuffer.wrap(new byte[24000]));
            assertTrue(uploading.await(2, TimeUnit.SECONDS));
            stream.append(ByteBuffer.wrap(new byte[24000]));
            stream.interrupt();
            stream.append(ByteBuffer.wrap(new byte[] {7, 8}));
            stream.flush();
            release.countDown();
            verify(session, timeout(2000).times(2)).audio(any());
            var ordered = inOrder(session);
            ordered.verify(session).audio(argThat(wav -> wav.length == 24044));
            ordered.verify(session).interrupt();
            ordered.verify(session).audio(argThat(wav -> wav.length == 46 && wav[44] == 7));
        } finally { release.countDown(); }
    }

    @Test void queueOverflowFailsWithoutBlockingOrAccumulatingUnboundedAudio() throws Exception {
        var session = mock(DigitalHumanProvider.Session.class);
        CountDownLatch uploading = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { uploading.countDown(); release.await(3, TimeUnit.SECONDS); return null; }).when(session).audio(any());
        AtomicInteger errors = new AtomicInteger();
        try (var stream = new DigitalHumanAudioStream(session, message -> errors.incrementAndGet())) {
            stream.append(ByteBuffer.wrap(new byte[24000]));
            assertTrue(uploading.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> stream.append(ByteBuffer.wrap(new byte[24000 * 30])));
            assertEquals(1, errors.get());
            stream.append(ByteBuffer.wrap(new byte[24000]));
            assertEquals(1, errors.get());
        } finally { release.countDown(); }
    }
}
