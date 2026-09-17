package com.robot.platform.robot.mission.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.command.outbox.service.RobotCommandDeliveryFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Bridges transport exhaustion into the Mission aggregate without letting the Outbox write Mission tables. */
@Service
@RequiredArgsConstructor
public class MissionCommandDeliveryFailureHandler implements RobotCommandDeliveryFailureHandler {
    private final MissionService missions;

    @Override
    public void exhausted(RobotCommandOutboxDO command, String errorCode, String errorMessage) {
        TenantUtils.execute(command.getTenantId(), () -> missions.commandDeliveryExhausted(command.getMissionId(),
                command.getMessageType(), errorCode, errorMessage));
    }
}
