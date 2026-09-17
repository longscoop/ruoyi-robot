package com.robot.platform.robot.status.service;

import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.status.model.HeartbeatPayload;

public interface RobotHeartbeatService {
    enum AcceptResult { ACCEPTED, DUPLICATE }
    AcceptResult accept(DeviceMqttIdentity device, RobotMessageEnvelope<HeartbeatPayload> heartbeat);
}
