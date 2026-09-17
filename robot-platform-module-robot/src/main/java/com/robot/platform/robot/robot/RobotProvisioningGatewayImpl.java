package com.robot.platform.robot.robot;

import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.device.spi.RobotProvisioningGateway;
import com.robot.platform.robot.robot.service.RobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Device owns the lifecycle transaction; this adapter only translates its provisioning port. */
@Component
@RequiredArgsConstructor
public class RobotProvisioningGatewayImpl implements RobotProvisioningGateway {
    private final RobotService robotService;

    @Override
    public long provision(RobotProvisionCommand command) {
        return robotService.provision(command);
    }

    @Override
    public void deprovision(long tenantId, long robotId) {
        robotService.deprovision(tenantId, robotId);
    }
}
