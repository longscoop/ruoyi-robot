package com.robot.platform.robot.mission.message.handler;

import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.mission.message.MissionEventPayload;
import com.robot.platform.robot.mission.service.MissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionActionMessageHandler extends AbstractMissionMessageHandler<MissionEventPayload> {
    public MissionActionMessageHandler(RobotMessageInboxService inbox, MissionService missions) { super(inbox, missions); }
    @Transactional(rollbackFor = Exception.class)
    public MessageHandleResult handle(DeviceIdentity device, RobotMessageEnvelope<MissionEventPayload> envelope) {
        return once(device, envelope, "MISSION_EVENT", (identity, message) -> {
            if (!"ACTION".equals(message.data().kind())) throw new IllegalArgumentException("mission event is not an action");
            return missions.handleAction(identity.deviceId(), message.requestId(), message.messageId(), message.data());
        });
    }
}
