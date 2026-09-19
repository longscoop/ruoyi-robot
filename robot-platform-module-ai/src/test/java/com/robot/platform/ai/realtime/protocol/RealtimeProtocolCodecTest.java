package com.robot.platform.ai.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeProtocolCodecTest {

    private static final Instant SERVER_TIME = Instant.parse("2026-09-19T06:00:00Z");

    @Test
    void decodesFixedClientJsonFixtures() {
        RealtimeProtocolCodec codec = new RealtimeProtocolCodec();

        RealtimeClientEvent sessionStart = codec.decodeClientText("""
                {
                  "type": "session.start",
                  "agentCode": "xiaoyou",
                  "identity": {
                    "memberId": 20003,
                    "type": "VOICEPRINT",
                    "confidence": 0.94
                  },
                  "audio": {
                    "codec": "PCM_S16LE",
                    "sampleRate": 16000,
                    "channels": 1
                  }
                }
                """);
        assertInstanceOf(RealtimeClientEvent.SessionStartEvent.class, sessionStart);
        RealtimeClientEvent.SessionStartEvent start = (RealtimeClientEvent.SessionStartEvent) sessionStart;
        assertEquals("session.start", start.type());
        assertEquals("xiaoyou", start.agentCode());
        assertEquals(20003L, start.identity().memberId());
        assertEquals("VOICEPRINT", start.identity().type());
        assertEquals(0.94, start.identity().confidence(), 0.0001);
        assertEquals(new RealtimeAudioFormat("PCM_S16LE", 16000, 1), start.audio());

        RealtimeClientEvent speechStarted = codec.decodeClientText(
                "{\"type\":\"input.speech_started\",\"eventId\":\"evt-1\"}");
        assertEquals(new RealtimeClientEvent.SpeechStartedEvent("evt-1"), speechStarted);

        RealtimeClientEvent speechStopped = codec.decodeClientText(
                "{\"type\":\"input.speech_stopped\",\"eventId\":\"evt-2\"}");
        assertEquals(new RealtimeClientEvent.SpeechStoppedEvent("evt-2"), speechStopped);

        RealtimeClientEvent sessionClose = codec.decodeClientText(
                "{\"type\":\"session.close\",\"reason\":\"CLIENT_CLOSE\"}");
        assertEquals(new RealtimeClientEvent.SessionCloseEvent("CLIENT_CLOSE"), sessionClose);
    }

    @Test
    void sessionStartContainsNoAuthoritativeTenantOrRobotIdentity() {
        var components = List.of(RealtimeClientEvent.SessionStartEvent.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList();

        assertEquals(List.of("agentCode", "identity", "audio"), components);

        RealtimeProtocolCodec codec = new RealtimeProtocolCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.decodeClientText("""
                {
                  "type": "session.start",
                  "agentCode": "xiaoyou",
                  "tenantId": 999,
                  "robotId": 888,
                  "audio": {"codec":"PCM_S16LE","sampleRate":16000,"channels":1}
                }
                """));
    }

    @Test
    void rejectsUnknownClientEventType() {
        RealtimeProtocolCodec codec = new RealtimeProtocolCodec();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> codec.decodeClientText("{\"type\":\"provider.qwen.event\"}"));

        assertTrue(error.getMessage().contains("provider.qwen.event"));
    }

    @Test
    void encodesEveryRequiredServerEventWithMonotonicEnvelope() {
        Clock clock = Clock.fixed(SERVER_TIME, ZoneOffset.UTC);
        RealtimeProtocolCodec codec = new RealtimeProtocolCodec(clock);
        RealtimeAudioFormat audio = new RealtimeAudioFormat("PCM_S16LE", 24000, 1);

        List<RealtimeServerEvent> events = List.of(
                new RealtimeServerEvent.SessionCreatedEvent("session-1", "NATIVE"),
                new RealtimeServerEvent.SessionErrorEvent("session-1", "BAD_REQUEST", "invalid frame"),
                new RealtimeServerEvent.InputTranscriptDeltaEvent("session-1", "turn-1", "你"),
                new RealtimeServerEvent.InputTranscriptDoneEvent("session-1", "turn-1", "你好"),
                new RealtimeServerEvent.AssistantTextDeltaEvent("session-1", "turn-1", "你"),
                new RealtimeServerEvent.AssistantTextDoneEvent("session-1", "turn-1", "你好"),
                new RealtimeServerEvent.AssistantAudioStartedEvent("session-1", "turn-1", audio),
                new RealtimeServerEvent.AssistantAudioDoneEvent("session-1", "turn-1"),
                new RealtimeServerEvent.AssistantInterruptedEvent("session-1", "turn-1", "BARGE_IN"),
                new RealtimeServerEvent.AssistantDoneEvent("session-1", "turn-1"),
                new RealtimeServerEvent.PlaybackStopEvent("session-1", "turn-1", "BARGE_IN"),
                new RealtimeServerEvent.ToolStartedEvent("session-1", "turn-1", "tool-1", "inspect_home"),
                new RealtimeServerEvent.ToolDoneEvent("session-1", "turn-1", "tool-1", "inspect_home", "ok"),
                new RealtimeServerEvent.SessionClosedEvent("session-1", "CLIENT_CLOSE")
        );

        List<String> expectedTypes = List.of(
                "session.created",
                "session.error",
                "input.transcript.delta",
                "input.transcript.done",
                "assistant.text.delta",
                "assistant.text.done",
                "assistant.audio.started",
                "assistant.audio.done",
                "assistant.interrupted",
                "assistant.done",
                "playback.stop",
                "tool.started",
                "tool.done",
                "session.closed"
        );

        for (int i = 0; i < events.size(); i++) {
            JsonNode json = JsonUtils.parseTree(codec.encodeServerEvent(events.get(i)));
            assertEquals(expectedTypes.get(i), json.path("type").asText());
            assertEquals("session-1", json.path("sessionId").asText());
            assertEquals(i + 1L, json.path("sequence").asLong());
            assertEquals(SERVER_TIME.toString(), json.path("serverTime").asText());

            if (events.get(i).turnScoped()) {
                assertEquals("turn-1", json.path("turnId").asText());
            } else {
                assertFalse(json.has("turnId"));
            }
        }
    }

    @Test
    void binaryAudioIsNotPartOfJsonProtocolCodecSurface() {
        var methodNames = List.of(RealtimeProtocolCodec.class.getDeclaredMethods()).stream()
                .map(method -> method.getName())
                .sorted()
                .toList();

        assertTrue(methodNames.contains("decodeClientText"));
        assertTrue(methodNames.contains("encodeServerEvent"));
        assertFalse(methodNames.stream().anyMatch(name -> name.toLowerCase().contains("base64")));
        assertFalse(methodNames.stream().anyMatch(name -> name.toLowerCase().contains("binary")));
    }
}
