package com.robot.platform.robot.mission.service;

import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.mqtt.RobotMessageEnvelopeCodec;
import com.robot.platform.mqtt.RobotProtocolException;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.mission.message.MissionAckPayload;
import com.robot.platform.robot.mission.message.MissionEventPayload;
import com.robot.platform.robot.mission.message.handler.MissionAckMessageHandler;
import com.robot.platform.robot.mission.message.handler.MissionActionMessageHandler;
import com.robot.platform.robot.mission.message.handler.MissionResultMessageHandler;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** DEVICE HTTP fallback uses the exact same typed Inbox handlers as MQTT. */
@Service
@RequiredArgsConstructor
public class DeviceMissionApplicationService {
    private final RobotMapper robots;
    private final RobotMessageEnvelopeCodec envelopes;
    private final MissionAckMessageHandler acks;
    private final MissionActionMessageHandler actions;
    private final MissionResultMessageHandler results;

    public MessageHandleResult acknowledge(long tenantId, long deviceId, long missionId, byte[] raw) {
        RobotMessageEnvelope<?> decoded = envelopes.decode(raw, MessageType.MISSION_ACK);
        if (!(decoded.data() instanceof MissionAckPayload payload) || payload.missionId() != missionId) throw new RobotProtocolException("mission acknowledgement payload mismatch");
        @SuppressWarnings("unchecked") RobotMessageEnvelope<MissionAckPayload> typed = (RobotMessageEnvelope<MissionAckPayload>) decoded;
        return acks.handle(identity(tenantId, deviceId), typed);
    }
    public MessageHandleResult event(long tenantId, long deviceId, long missionId, byte[] raw) {
        RobotMessageEnvelope<?> decoded = envelopes.decode(raw, MessageType.MISSION_EVENT);
        if (!(decoded.data() instanceof MissionEventPayload payload) || payload.missionId() != missionId) throw new RobotProtocolException("mission event payload mismatch");
        @SuppressWarnings("unchecked") RobotMessageEnvelope<MissionEventPayload> typed = (RobotMessageEnvelope<MissionEventPayload>) decoded;
        DeviceIdentity identity = identity(tenantId, deviceId);
        return "ACTION".equals(payload.kind()) ? actions.handle(identity, typed) : results.handle(identity, typed);
    }
    private DeviceIdentity identity(long tenantId, long deviceId) {
        RobotDO robot = robots.selectByTenantAndDeviceId(tenantId, deviceId);
        if (robot == null) throw new AccessDeniedException("device is not bound to a robot");
        return new DeviceIdentity(deviceId, tenantId, robot.getProductId(), "device-" + deviceId, "http", 0);
    }
}
