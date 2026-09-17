package com.robot.platform.robot.mission.message;

import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.robot.mission.message.handler.MissionActionMessageHandler;
import com.robot.platform.robot.mission.service.MissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The production change this catches: duplicate broker delivery replays an action state transition. */
class MissionMessageHandlerTest {
    private final RobotMessageInboxService inbox = mock(RobotMessageInboxService.class);
    private final MissionService missions = mock(MissionService.class);
    private final MissionActionMessageHandler handler = new MissionActionMessageHandler(inbox, missions);

    @AfterEach
    void clearTenant() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }

    @Test
    void duplicateActionMessageDoesNotReachMissionService() {
        when(inbox.insertOrVerify(any())).thenReturn(RobotMessageInboxService.InsertResult.DUPLICATE);
        RobotMessageEnvelope<MissionEventPayload> envelope = new RobotMessageEnvelope<>("01K5K8VJGR9VK8T3H7ZQX3W1AB", "request-1",
                1_757_894_400_000L, 1, MessageType.MISSION_EVENT, MessageSource.ROBOT,
                new MissionEventPayload("ACTION", 40L, 4L, "SUCCESS", null, null));

        MessageHandleResult result = handler.handle(new DeviceIdentity(20L, 10L, 1L, "device", "username", 1), envelope);

        assertThat(result).isEqualTo(MessageHandleResult.DUPLICATE);
        verifyNoInteractions(missions);
    }
}
