package com.robot.platform.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict envelope codec. The payload byte limit is enforced before JSON parsing; decoding then
 * accepts only the fixed envelope fields and DTO classes selected by the registry.
 */
public final class RobotMessageEnvelopeCodec {
    private static final Pattern ULID = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final long EARLIEST_TIMESTAMP = 1_577_836_800_000L;
    private static final Set<String> FIELDS = Set.of("messageId", "requestId", "timestamp", "version", "type", "source", "data");
    private final RobotMessageTypeRegistry registry;
    private final int maxPayloadBytes;
    private final Clock clock;
    private final ObjectMapper mapper;

    public RobotMessageEnvelopeCodec(RobotMessageTypeRegistry registry, int maxPayloadBytes, Clock clock) {
        if (registry == null || maxPayloadBytes < 1 || clock == null) throw new IllegalArgumentException("invalid codec configuration");
        this.registry = registry;
        this.maxPayloadBytes = maxPayloadBytes;
        this.clock = clock;
        this.mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public byte[] encode(RobotMessageEnvelope<?> envelope) {
        validateEnvelope(envelope);
        registry.requirePayloadInstance(envelope.type(), envelope.version(), envelope.data());
        ObjectNode node = mapper.createObjectNode();
        node.put("messageId", envelope.messageId());
        node.put("requestId", envelope.requestId());
        node.put("timestamp", envelope.timestamp());
        node.put("version", envelope.version());
        node.put("type", envelope.type().name());
        node.put("source", envelope.source().name());
        node.set("data", mapper.valueToTree(envelope.data()));
        try {
            byte[] payload = mapper.writeValueAsBytes(node);
            if (payload.length > maxPayloadBytes) throw new RobotProtocolException("payload exceeds configured limit");
            return payload;
        } catch (JsonProcessingException e) {
            throw new RobotProtocolException("unable to encode envelope", e);
        }
    }

    public RobotMessageEnvelope<?> decode(byte[] payload, MessageType expectedType) {
        if (payload == null || payload.length == 0 || payload.length > maxPayloadBytes) {
            throw new RobotProtocolException("payload exceeds configured limit");
        }
        try {
            JsonNode root = mapper.readTree(payload);
            if (!(root instanceof ObjectNode object) || !object.fieldNames().hasNext()) throw new RobotProtocolException("envelope must be an object");
            requireOnlyEnvelopeFields(object);
            String messageId = requiredText(object, "messageId");
            String requestId = requiredText(object, "requestId");
            long timestamp = requiredLong(object, "timestamp");
            int version = requiredInt(object, "version");
            MessageType type = parseEnum(MessageType.class, requiredText(object, "type"), "type");
            MessageSource source = parseEnum(MessageSource.class, requiredText(object, "source"), "source");
            if (expectedType != null && type != expectedType) throw new RobotProtocolException("unexpected message type");
            JsonNode data = object.get("data");
            if (data == null || data.isNull() || !data.isObject()) throw new RobotProtocolException("data must be an object");
            RobotMessageDescriptor<?> descriptor = registry.requiredDescriptor(type, version);
            Object decodedData = mapper.treeToValue(data, descriptor.payloadType()); registry.validate(descriptor, decodedData);
            RobotMessageEnvelope<?> envelope = new RobotMessageEnvelope<>(messageId, requestId, timestamp, version, type, source, decodedData);
            validateEnvelope(envelope);
            return envelope;
        } catch (RobotProtocolException e) {
            throw e;
        } catch (Exception e) {
            throw new RobotProtocolException("malformed envelope", e);
        }
    }

    private void validateEnvelope(RobotMessageEnvelope<?> envelope) {
        if (envelope == null || envelope.type() == null || envelope.source() == null || envelope.version() < 1
                || envelope.messageId() == null || !ULID.matcher(envelope.messageId()).matches()
                || envelope.requestId() == null || !REQUEST_ID.matcher(envelope.requestId()).matches()
                || envelope.timestamp() < EARLIEST_TIMESTAMP || envelope.timestamp() > clock.millis() + 300_000L) {
            throw new RobotProtocolException("invalid envelope metadata");
        }
    }

    private static void requireOnlyEnvelopeFields(ObjectNode object) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) if (!FIELDS.contains(names.next())) throw new RobotProtocolException("unknown envelope field");
        if (!object.fieldNames().hasNext() && object.size() != FIELDS.size()) throw new RobotProtocolException("missing envelope field");
        for (String field : FIELDS) if (!object.has(field)) throw new RobotProtocolException("missing envelope field");
    }

    private static String requiredText(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) throw new RobotProtocolException("invalid " + name);
        return node.textValue();
    }
    private static long requiredLong(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) throw new RobotProtocolException("invalid " + name);
        return node.longValue();
    }
    private static int requiredInt(ObjectNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) throw new RobotProtocolException("invalid " + name);
        return node.intValue();
    }
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try { return Enum.valueOf(type, value); } catch (RuntimeException e) { throw new RobotProtocolException("invalid " + field, e); }
    }
}
