package com.robot.platform.ai.realtime.protocol;

public sealed interface RealtimeServerEvent {

    String type();

    String sessionId();

    default String turnId() {
        return null;
    }

    default boolean turnScoped() {
        return turnId() != null;
    }

    record SessionCreatedEvent(String sessionId, String mode) implements RealtimeServerEvent {
        @Override
        public String type() {
            return "session.created";
        }
    }

    record SessionErrorEvent(String sessionId, String code, String message) implements RealtimeServerEvent {
        @Override
        public String type() {
            return "session.error";
        }
    }

    record InputTranscriptDeltaEvent(String sessionId, String turnId, String delta)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "input.transcript.delta";
        }
    }

    record InputTranscriptDoneEvent(String sessionId, String turnId, String text)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "input.transcript.done";
        }
    }

    record AssistantTextDeltaEvent(String sessionId, String turnId, String delta)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.text.delta";
        }
    }

    record AssistantTextDoneEvent(String sessionId, String turnId, String text)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.text.done";
        }
    }

    record AssistantAudioStartedEvent(String sessionId, String turnId, RealtimeAudioFormat audio)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.audio.started";
        }
    }

    record AssistantAudioDoneEvent(String sessionId, String turnId) implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.audio.done";
        }
    }

    record AssistantInterruptedEvent(String sessionId, String turnId, String reason)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.interrupted";
        }
    }

    record AssistantDoneEvent(String sessionId, String turnId) implements RealtimeServerEvent {
        @Override
        public String type() {
            return "assistant.done";
        }
    }

    record PlaybackStopEvent(String sessionId, String turnId, String reason)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "playback.stop";
        }
    }

    record ToolStartedEvent(String sessionId, String turnId, String toolCallId, String name)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "tool.started";
        }
    }

    record ToolDoneEvent(String sessionId, String turnId, String toolCallId, String name, String result)
            implements RealtimeServerEvent {
        @Override
        public String type() {
            return "tool.done";
        }
    }

    record DigitalHumanConfigEvent(String sessionId, String configJson) implements RealtimeServerEvent { public String type(){return "digital_human.config";} }\n\n    record DigitalHumanStateEvent(String sessionId, String turnId, String state, String actionCode) implements RealtimeServerEvent { public String type(){return "digital_human.state";} }\n\n    record DigitalHumanVisemeEvent(String sessionId, String turnId, String timelineJson) implements RealtimeServerEvent { public String type(){return "digital_human.viseme";} }\n\n    record SessionClosedEvent(String sessionId, String reason) implements RealtimeServerEvent {
        @Override
        public String type() {
            return "session.closed";
        }
    }
}
