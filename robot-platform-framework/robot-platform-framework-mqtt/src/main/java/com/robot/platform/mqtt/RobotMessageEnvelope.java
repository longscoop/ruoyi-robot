package com.robot.platform.mqtt;

/**
 * Immutable wire envelope. Its data is trustworthy only after {@link RobotMessageEnvelopeCodec}
 * has checked the byte limit, registry-selected DTO, metadata and schema version.
 */
public record RobotMessageEnvelope<T>(String messageId, String requestId, long timestamp, int version,
                                      MessageType type, MessageSource source, T data) { }
