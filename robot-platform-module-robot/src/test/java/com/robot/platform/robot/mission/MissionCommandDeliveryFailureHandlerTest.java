package com.robot.platform.robot.mission;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.mission.service.MissionCommandDeliveryFailureHandler;
import com.robot.platform.robot.mission.service.MissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Verifies the transport-to-domain bridge cannot leak a scheduler tenant into the Mission aggregate. */
class MissionCommandDeliveryFailureHandlerTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void exhaustionReentersCommandTenantAndForwardsStableFailureFacts() {
        MissionService missions = mock(MissionService.class);
        RobotCommandOutboxDO command = new RobotCommandOutboxDO();
        command.setTenantId(42L); command.setMissionId(99L); command.setMessageType("MISSION_START");
        MissionCommandDeliveryFailureHandler handler = new MissionCommandDeliveryFailureHandler(missions);
        TenantContextHolder.setTenantId(7L);

        handler.exhausted(command, "MISSION_COMMAND_DELIVERY_EXHAUSTED", "bad durable envelope");

        verify(missions).commandDeliveryExhausted(99L, "MISSION_START", "MISSION_COMMAND_DELIVERY_EXHAUSTED", "bad durable envelope");
        assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(7L);
    }
}
