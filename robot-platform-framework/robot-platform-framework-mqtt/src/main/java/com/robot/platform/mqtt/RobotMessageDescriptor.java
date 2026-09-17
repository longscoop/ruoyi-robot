package com.robot.platform.mqtt;

import java.util.Objects;
import java.util.Set;

/** Registry-owned inbound policy: the wire type alone never selects a handler. */
public record RobotMessageDescriptor<T>(MessageType type, int version, Class<T> payloadType,
                                        Set<MessageSource> allowedSources, Set<RobotTopic.Channel> allowedChannels,
                                        RobotPayloadValidator<T> validator) {
    public RobotMessageDescriptor {
        Objects.requireNonNull(type);
        Objects.requireNonNull(payloadType);
        if (version < 1 || allowedSources == null || allowedChannels == null
                || allowedSources.isEmpty() != allowedChannels.isEmpty()) {
            throw new IllegalArgumentException("invalid descriptor");
        }
        allowedSources = Set.copyOf(allowedSources);
        allowedChannels = Set.copyOf(allowedChannels);
        validator = validator == null ? value -> { } : validator;
    }
    public static <T> RobotMessageDescriptor<T> of(MessageType type, int version, Class<T> payloadType, Set<MessageSource> sources, Set<RobotTopic.Channel> channels) { return new RobotMessageDescriptor<>(type, version, payloadType, sources, channels, null); }
    /** Only explicit source/channel policy can enter inbound dispatch. Empty sets represent outbound-only schema. */
    public boolean supportsInbound() { return !allowedSources.isEmpty(); }
}
