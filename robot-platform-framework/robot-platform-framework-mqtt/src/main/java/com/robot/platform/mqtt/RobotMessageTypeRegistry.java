package com.robot.platform.mqtt;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whitelist for protocol evolution. Decoding never accepts a caller-selected data class: only a
 * registered (type, version) pair determines the DTO that Jackson may instantiate.
 */
public final class RobotMessageTypeRegistry {
    private final Map<Key, RobotMessageDescriptor<?>> descriptors = new ConcurrentHashMap<>();

    /** Declares an outbound schema only; inbound handlers require an explicit source/channel descriptor. */
    public <T> RobotMessageTypeRegistry register(MessageType type, int version, Class<T> payloadType) {
        return register(new RobotMessageDescriptor<>(type, version, payloadType, Set.of(), Set.of(), null));
    }
    public <T> RobotMessageTypeRegistry register(RobotMessageDescriptor<T> descriptor) {
        if (descriptors.putIfAbsent(new Key(descriptor.type(), descriptor.version()), descriptor) != null) {
            throw new IllegalStateException("message type/version already registered");
        }
        return this;
    }

    public Class<?> requiredPayloadType(MessageType type, int version) {
        return requiredDescriptor(type, version).payloadType();
    }
    public int size() { return descriptors.size(); }
    public boolean isEmpty() { return descriptors.isEmpty(); }
    public RobotMessageDescriptor<?> requiredDescriptor(MessageType type, int version) {
        RobotMessageDescriptor<?> descriptor = descriptors.get(new Key(type, version));
        if (descriptor == null) throw new RobotProtocolException("unregistered message type or version");
        return descriptor;
    }

    public void requirePayloadInstance(MessageType type, int version, Object payload) {
        RobotMessageDescriptor<?> descriptor = requiredDescriptor(type, version); Class<?> expected = descriptor.payloadType();
        if (payload == null || !expected.isInstance(payload)) {
            throw new RobotProtocolException("payload does not match registered message type");
        }
        validate(descriptor, payload);
    }
    @SuppressWarnings("unchecked") public void validate(RobotMessageDescriptor<?> descriptor, Object payload) { try { ((RobotMessageDescriptor<Object>) descriptor).validator().validate(payload); } catch (RobotProtocolException e) { throw e; } catch (RuntimeException e) { throw new RobotProtocolException("payload validation failed", e); } }

    private record Key(MessageType type, int version) {
        private Key { Objects.requireNonNull(type, "type"); }
    }
}
