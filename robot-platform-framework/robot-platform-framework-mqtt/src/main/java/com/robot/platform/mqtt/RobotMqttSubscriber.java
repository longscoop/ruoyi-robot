package com.robot.platform.mqtt;

/** Handler registration is checked against the registry, preventing callers from selecting a different data DTO. */
public interface RobotMqttSubscriber {
    <T> void register(MessageType type, Class<T> payloadType, RobotMessageHandler<T> handler);
    <T> void register(RobotMessageDescriptor<T> descriptor, RobotInboundMessageHandler<T> handler);
    /** Cloud-only exact wildcard for authenticated robot state ingress; never exposes command/OTA. */
    default java.util.concurrent.CompletionStage<Void> subscribeRobotStateWildcard() {
        return java.util.concurrent.CompletableFuture.failedFuture(new RobotProtocolException("state wildcard subscription unsupported"));
    }
}
