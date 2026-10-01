package com.robot.platform.ai.digitalhuman;

import com.robot.platform.ai.digitalhuman.provider.*;
import com.robot.platform.ai.digitalhuman.service.DigitalHumanPreviewSessions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalHumanPreviewSessionsTest {
    @Test void previewHandlesAreScopedToTenantUserAndDigitalHuman() {
        var providers = mock(DigitalHumanProviders.class);
        var upstream = mock(DigitalHumanProvider.Session.class);
        when(providers.open("config", "v=0")).thenReturn(upstream);
        when(upstream.answerSdp()).thenReturn("v=0 answer");
        var sessions = new DigitalHumanPreviewSessions(providers);
        try {
            var answer = sessions.open(1, 2, 3, "config", "v=0");
            assertEquals("answer", answer.type());
            assertThrows(RuntimeException.class, () -> sessions.speak(9, 2, 3, answer.sessionId(), "hello"));
            assertThrows(RuntimeException.class, () -> sessions.interrupt(1, 9, 3, answer.sessionId()));
            assertThrows(RuntimeException.class, () -> sessions.speaking(1, 2, 9, answer.sessionId()));
            verify(upstream, never()).speak(any());
            sessions.speak(1, 2, 3, answer.sessionId(), "hello");
            verify(upstream).speak("hello");
            sessions.close(1, 2, 3, answer.sessionId());
            verify(upstream).close();
            assertThrows(RuntimeException.class, () -> sessions.speaking(1, 2, 3, answer.sessionId()));
        } finally { sessions.destroy(); }
    }
}
