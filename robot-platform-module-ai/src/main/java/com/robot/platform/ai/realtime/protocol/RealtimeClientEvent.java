package com.robot.platform.ai.realtime.protocol;

public sealed interface RealtimeClientEvent {

    String type();

    record CandidateIdentity(Long memberId, String type, Double confidence) {
        public CandidateIdentity {
            if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
                throw new IllegalArgumentException("identity confidence must be between 0 and 1");
            }
        }
    }

    record SessionStartEvent(String agentCode, CandidateIdentity identity,
                             RealtimeAudioFormat audio) implements RealtimeClientEvent {
        @Override
        public String type() {
            return "session.start";
        }
    }

    record SpeechStartedEvent(String eventId) implements RealtimeClientEvent {
        @Override
        public String type() {
            return "input.speech_started";
        }
    }

    record SpeechStoppedEvent(String eventId) implements RealtimeClientEvent {
        @Override
        public String type() {
            return "input.speech_stopped";
        }
    }

    record SessionCloseEvent(String reason) implements RealtimeClientEvent {
        @Override
        public String type() {
            return "session.close";
        }
    }
}
