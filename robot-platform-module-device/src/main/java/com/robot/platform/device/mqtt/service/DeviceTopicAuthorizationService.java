package com.robot.platform.device.mqtt.service;

import com.robot.platform.mqtt.MqttAuthorizationAction;
import com.robot.platform.mqtt.RobotProtocolException;
import com.robot.platform.mqtt.RobotTopic;
import org.springframework.stereotype.Service;

/**
 * Pure policy over a server-resolved identity. It authorizes only exact device topics and always
 * rejects retained messages, non-QoS-1 delivery and unparseable/non-canonical topics before channel checks.
 */
@Service
public class DeviceTopicAuthorizationService {
    public boolean isAllowed(DeviceMqttIdentity identity, MqttAuthorizationAction action, String rawTopic, Integer qos, Boolean retain) {
        if (identity == null || action == null || qos == null || qos != 1) return false;
        if (action == MqttAuthorizationAction.PUBLISH && !Boolean.FALSE.equals(retain)) return false;
        final RobotTopic topic;
        try { topic = RobotTopic.parse(rawTopic); } catch (RobotProtocolException e) { return false; }
        if (!rawTopic.equals(topic.value())) return false;
        if (!identity.tenantNamespace().equals(topic.tenantNamespace()) || !identity.productKey().equals(topic.productKey())
                || !identity.deviceSn().equals(topic.deviceSn())) return false;
        return switch (action) {
            case SUBSCRIBE -> topic.channel() == RobotTopic.Channel.COMMAND || topic.channel() == RobotTopic.Channel.OTA;
            case PUBLISH -> topic.channel() == RobotTopic.Channel.STATE || topic.channel() == RobotTopic.Channel.EVENT;
        };
    }
}
