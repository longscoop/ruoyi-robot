package com.robot.platform.ai.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.time.Clock;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class RealtimeProtocolCodec {

    private static final Set<String> SESSION_START_FIELDS = Set.of("type", "agentCode", "identity", "audio");
    private static final Set<String> IDENTITY_FIELDS = Set.of("memberId", "type", "confidence");
    private static final Set<String> AUDIO_FIELDS = Set.of("codec", "sampleRate", "channels");

    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public RealtimeProtocolCodec() {
        this(Clock.systemUTC());
    }

    public RealtimeProtocolCodec(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    public RealtimeClientEvent decodeClientText(String json) {
        final JsonNode root;
        try {
            root = JsonUtils.parseTree(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid realtime client JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Realtime client frame must be a JSON object");
        }

        String type = requireText(root, "type");
        return switch (type) {
            case "session.start" -> decodeSessionStart(root);
            case "input.speech_started" ->
                    new RealtimeClientEvent.SpeechStartedEvent(optionalText(root, "eventId"));
            case "input.speech_stopped" ->
                    new RealtimeClientEvent.SpeechStoppedEvent(optionalText(root, "eventId"));
            case "session.close" ->
                    new RealtimeClientEvent.SessionCloseEvent(optionalText(root, "reason"));
            default -> throw new IllegalArgumentException("Unsupported realtime client event type: " + type);
        };
    }

    public String encodeServerEvent(RealtimeServerEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("server event must not be null");
        }
        requireNonBlank(event.sessionId(), "server event sessionId");
        if (event.turnScoped()) {
            requireNonBlank(event.turnId(), "turn-scoped server event turnId");
        }

        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("type", event.type());
        root.put("sessionId", event.sessionId());
        if (event.turnScoped()) {
            root.put("turnId", event.turnId());
        }
        root.put("sequence", sequence.incrementAndGet());
        root.put("serverTime", clock.instant().toString());
        writePayload(root, event);
        return JsonUtils.toJsonString(root);
    }

    private static RealtimeClientEvent decodeSessionStart(JsonNode root) {
        rejectUnknownFields(root, SESSION_START_FIELDS, "session.start");
        String agentCode = requireText(root, "agentCode");
        RealtimeClientEvent.CandidateIdentity identity = decodeIdentity(root.get("identity"));
        RealtimeAudioFormat audio = decodeAudio(root.get("audio"));
        return new RealtimeClientEvent.SessionStartEvent(agentCode, identity, audio);
    }

    private static RealtimeClientEvent.CandidateIdentity decodeIdentity(JsonNode identity) {
        if (identity == null || identity.isNull()) {
            return null;
        }
        if (!identity.isObject()) {
            throw new IllegalArgumentException("session.start identity must be an object");
        }
        rejectUnknownFields(identity, IDENTITY_FIELDS, "session.start identity");

        Long memberId = null;
        JsonNode memberIdNode = identity.get("memberId");
        if (memberIdNode != null && !memberIdNode.isNull()) {
            if (!memberIdNode.isIntegralNumber()) {
                throw new IllegalArgumentException("identity memberId must be an integer");
            }
            memberId = memberIdNode.longValue();
        }

        String identityType = optionalText(identity, "type");
        Double confidence = null;
        JsonNode confidenceNode = identity.get("confidence");
        if (confidenceNode != null && !confidenceNode.isNull()) {
            if (!confidenceNode.isNumber()) {
                throw new IllegalArgumentException("identity confidence must be numeric");
            }
            confidence = confidenceNode.doubleValue();
        }
        return new RealtimeClientEvent.CandidateIdentity(memberId, identityType, confidence);
    }

    private static RealtimeAudioFormat decodeAudio(JsonNode audio) {
        if (audio == null || audio.isNull() || !audio.isObject()) {
            throw new IllegalArgumentException("session.start audio must be an object");
        }
        rejectUnknownFields(audio, AUDIO_FIELDS, "session.start audio");
        return new RealtimeAudioFormat(
                requireText(audio, "codec"),
                requirePositiveInt(audio, "sampleRate"),
                requirePositiveInt(audio, "channels"));
    }

    private static void writePayload(ObjectNode root, RealtimeServerEvent event) {
        if (event instanceof RealtimeServerEvent.SessionCreatedEvent value) {
            putIfNotNull(root, "mode", value.mode());
        } else if (event instanceof RealtimeServerEvent.SessionErrorEvent value) {
            putIfNotNull(root, "code", value.code());
            putIfNotNull(root, "message", value.message());
        } else if (event instanceof RealtimeServerEvent.InputTranscriptDeltaEvent value) {
            putIfNotNull(root, "delta", value.delta());
        } else if (event instanceof RealtimeServerEvent.InputTranscriptDoneEvent value) {
            putIfNotNull(root, "text", value.text());
        } else if (event instanceof RealtimeServerEvent.AssistantTextDeltaEvent value) {
            putIfNotNull(root, "delta", value.delta());
        } else if (event instanceof RealtimeServerEvent.AssistantTextDoneEvent value) {
            putIfNotNull(root, "text", value.text());
        } else if (event instanceof RealtimeServerEvent.AssistantAudioStartedEvent value) {
            if (value.audio() != null) {
                root.set("audio", JsonUtils.getObjectMapper().valueToTree(value.audio()));
            }
        } else if (event instanceof RealtimeServerEvent.AssistantInterruptedEvent value) {
            putIfNotNull(root, "reason", value.reason());
        } else if (event instanceof RealtimeServerEvent.PlaybackStopEvent value) {
            putIfNotNull(root, "reason", value.reason());
        } else if (event instanceof RealtimeServerEvent.ToolStartedEvent value) {
            putIfNotNull(root, "toolCallId", value.toolCallId());
            putIfNotNull(root, "name", value.name());
        } else if (event instanceof RealtimeServerEvent.ToolDoneEvent value) {
            putIfNotNull(root, "toolCallId", value.toolCallId());
            putIfNotNull(root, "name", value.name());
            putIfNotNull(root, "result", value.result());
        } else if (event instanceof RealtimeServerEvent.SessionClosedEvent value) {
            putIfNotNull(root, "reason", value.reason());
        }
    }

    private static String requireText(JsonNode node, String field) {
        String value = optionalText(node, field);
        requireNonBlank(value, field);
        return value.trim();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.textValue();
    }

    private static int requirePositiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt() || value.intValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.intValue();
    }

    private static void rejectUnknownFields(JsonNode node, Set<String> allowed, String label) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(label + " contains unsupported field: " + field);
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    private static void putIfNotNull(ObjectNode root, String field, String value) {
        if (value != null) {
            root.put(field, value);
        }
    }
}
