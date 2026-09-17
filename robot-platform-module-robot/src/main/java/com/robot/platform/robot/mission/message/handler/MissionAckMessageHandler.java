package com.robot.platform.robot.mission.message.handler;

import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.mission.message.MissionAckPayload;
import com.robot.platform.robot.mission.service.MissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionAckMessageHandler extends AbstractMissionMessageHandler<MissionAckPayload> {
    public MissionAckMessageHandler(RobotMessageInboxService inbox, MissionService missions) { super(inbox, missions); }
    @Transactional(rollbackFor = Exception.class)
    public MessageHandleResult handle(DeviceIdentity device, RobotMessageEnvelope<MissionAckPayload> envelope) {
        return once(device, envelope, "MISSION_ACK", (identity, message) ->
                missions.acknowledge(identity.deviceId(), message.requestId(), message.messageId(), message.data()));
    }
}
