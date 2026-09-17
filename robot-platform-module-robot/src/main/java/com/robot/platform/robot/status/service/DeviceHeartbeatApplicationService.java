package com.robot.platform.robot.status.service;

import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.mqtt.RobotMessageEnvelopeCodec;
import com.robot.platform.mqtt.RobotProtocolException;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.controller.device.vo.DeviceRobotConfigRespVO;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Application boundary shared by Device HTTP endpoints; controllers never access persistence directly. */
@Service
@RequiredArgsConstructor
public class DeviceHeartbeatApplicationService {
    private final RobotMapper robots;
    private final RobotHeartbeatService heartbeats;
    private final RobotMessageEnvelopeCodec envelopes;

    public RobotHeartbeatService.AcceptResult accept(long tenantId, long deviceId, byte[] rawBody) {
        RobotDO robot = requireBoundRobot(tenantId, deviceId);
        RobotMessageEnvelope<?> decoded = envelopes.decode(rawBody, MessageType.HEARTBEAT);
        if (!(decoded.data() instanceof HeartbeatPayload)) {
            throw new RobotProtocolException("heartbeat payload type mismatch");
        }
        @SuppressWarnings("unchecked")
        RobotMessageEnvelope<HeartbeatPayload> heartbeat = (RobotMessageEnvelope<HeartbeatPayload>) decoded;
        return heartbeats.accept(identity(tenantId, deviceId, robot.getId()), heartbeat);
    }

    public DeviceRobotConfigRespVO config(long tenantId, long deviceId) {
        return new DeviceRobotConfigRespVO(requireBoundRobot(tenantId, deviceId).getId(), 30);
    }

    private RobotDO requireBoundRobot(long tenantId, long deviceId) {
        RobotDO robot = robots.selectByTenantAndDeviceId(tenantId, deviceId);
        if (robot == null) throw new AccessDeniedException("device is not bound to this robot");
        return robot;
    }

    private static DeviceMqttIdentity identity(long tenantId, long deviceId, long robotId) {
        return new DeviceMqttIdentity(deviceId, tenantId, robotId, "http", "http", "device-" + deviceId,
                "http/http/device-" + deviceId, "", 0);
    }
}
