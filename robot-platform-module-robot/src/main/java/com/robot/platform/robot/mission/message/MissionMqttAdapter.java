package com.robot.platform.robot.mission.message;

import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.*;
import com.robot.platform.robot.mission.message.handler.MissionAckMessageHandler;
import com.robot.platform.robot.mission.message.handler.MissionActionMessageHandler;
import com.robot.platform.robot.mission.message.handler.MissionResultMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Authenticates the canonical MQTT topic before passing an already typed envelope to handlers. */
@Component
@RequiredArgsConstructor
public class MissionMqttAdapter implements SmartLifecycle {
    private final ObjectProvider<RobotMqttSubscriber> subscriber;
    private final RobotMessageTypeRegistry registry;
    private final DeviceMqttAuthenticationService identities;
    private final MissionAckMessageHandler acks;
    private final MissionActionMessageHandler actions;
    private final MissionResultMessageHandler results;
    private volatile boolean running;
    private volatile boolean registered;

    @Override public void start() {
        RobotMqttSubscriber mqtt = subscriber.getIfAvailable();
        if (mqtt == null || running) return;
        if (!registered) {
            @SuppressWarnings("unchecked") RobotMessageDescriptor<MissionAckPayload> ack = (RobotMessageDescriptor<MissionAckPayload>) registry.requiredDescriptor(MessageType.MISSION_ACK, 1);
            @SuppressWarnings("unchecked") RobotMessageDescriptor<MissionEventPayload> event = (RobotMessageDescriptor<MissionEventPayload>) registry.requiredDescriptor(MessageType.MISSION_EVENT, 1);
            mqtt.register(ack, this::ack); mqtt.register(event, this::event); registered = true;
        }
        mqtt.subscribeRobotStateWildcard().toCompletableFuture().join(); running = true;
    }
    @Override public void stop() { running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 45; }

    private void ack(RobotInboundMessage<MissionAckPayload> inbound) { acks.handle(identity(inbound.topic()), inbound.envelope()); }
    private void event(RobotInboundMessage<MissionEventPayload> inbound) {
        if ("ACTION".equals(inbound.envelope().data().kind())) actions.handle(identity(inbound.topic()), inbound.envelope());
        else if ("RESULT".equals(inbound.envelope().data().kind())) results.handle(identity(inbound.topic()), inbound.envelope());
        else throw new RobotProtocolException("unsupported mission event kind");
    }
    private DeviceIdentity identity(RobotTopic topic) {
        String username = topic.tenantNamespace() + "/" + topic.productKey() + "/" + topic.deviceSn();
        DeviceMqttIdentity found = identities.findActiveByUsername(username).filter(value -> value.tenantNamespace().equals(topic.tenantNamespace())
                        && value.productKey().equals(topic.productKey()) && value.deviceSn().equals(topic.deviceSn())
                        && topic.channel() == RobotTopic.Channel.STATE)
                .orElseThrow(() -> new RobotProtocolException("topic does not match active device identity"));
        return new DeviceIdentity(found.deviceId(), found.tenantId(), 0L, found.deviceSn(), found.mqttUsername(), found.credentialVersion());
    }
}
