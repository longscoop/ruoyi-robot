package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractRealtimeVoiceClientContractTest {

    protected abstract Harness createHarness();

    @Test
    void openAppendReceiveCancelAndCloseFollowCommonContract() {
        Harness harness = createHarness();
        List<ProviderEvent> events = new ArrayList<>();
        RealtimeProviderSession session = harness.open(events::add);
        assertNotNull(session);

        harness.ready();
        session.speechStarted();
        session.appendAudio(ByteBuffer.wrap(new byte[]{1, 2, 3, 4}));
        session.speechStopped();
        harness.emitTranscriptTextAudio();

        assertTrue(events.stream().anyMatch(ProviderEvent.TranscriptDone.class::isInstance));
        assertTrue(events.stream().anyMatch(ProviderEvent.TextDelta.class::isInstance));
        assertTrue(events.stream().anyMatch(ProviderEvent.AudioDelta.class::isInstance));
        assertTrue(events.stream().anyMatch(ProviderEvent.AudioDone.class::isInstance));
        harness.assertAudioForwarded(new byte[]{1, 2, 3, 4});

        assertDoesNotThrow(session::cancelCurrentResponse);
        assertDoesNotThrow(session::close);
        harness.assertClosed();
    }

    protected interface Harness {
        RealtimeProviderSession open(RealtimeProviderListener listener);

        void ready();

        void emitTranscriptTextAudio();

        void assertAudioForwarded(byte[] expected);

        void assertClosed();
    }
}
