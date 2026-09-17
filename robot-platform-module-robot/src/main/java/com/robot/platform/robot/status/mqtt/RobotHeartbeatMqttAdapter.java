package com.robot.platform.robot.status.mqtt;

import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.*;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.context.SmartLifecycle;

/** Transport adapter authenticates from the canonical topic only; envelope data cannot select identity. */
@Component @RequiredArgsConstructor
public class RobotHeartbeatMqttAdapter implements SmartLifecycle {
    private final ObjectProvider<RobotMqttSubscriber> subscriber;
    private final RobotMessageTypeRegistry registry;
    private final DeviceMqttAuthenticationService identities;
    private final RobotHeartbeatService heartbeats;

    private volatile boolean running;
    private volatile boolean registered;
    @Override public void start() {
        RobotMqttSubscriber mqtt = subscriber.getIfAvailable();
        if (mqtt != null && !running) {
            if (!registered) {
                @SuppressWarnings("unchecked") RobotMessageDescriptor<HeartbeatPayload> descriptor =
                        (RobotMessageDescriptor<HeartbeatPayload>) registry.requiredDescriptor(MessageType.HEARTBEAT, 1);
                mqtt.register(descriptor, this::handle);
                registered = true;
            }
            mqtt.subscribeRobotStateWildcard().toCompletableFuture().join();
            running = true;
        }
    }
    @Override public void stop() { running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 50; }
    void handle(RobotInboundMessage<HeartbeatPayload> inbound) {
        RobotTopic topic = inbound.topic();
        String username = topic.tenantNamespace() + "/" + topic.productKey() + "/" + topic.deviceSn();
        DeviceMqttIdentity identity = identities.findActiveByUsername(username)
                .filter(found -> found.tenantNamespace().equals(topic.tenantNamespace()) && found.productKey().equals(topic.productKey())
                        && found.deviceSn().equals(topic.deviceSn()) && topic.channel() == RobotTopic.Channel.STATE)
                .orElseThrow(() -> new RobotProtocolException("topic does not match active device identity"));
        heartbeats.accept(identity, inbound.envelope());
    }
}
