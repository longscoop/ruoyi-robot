package com.robot.platform.device.spi;

/** Port implemented by the robot module; device owns the provisioning transaction boundary. */
public interface RobotProvisioningGateway {
    long provision(RobotProvisionCommand command);
    void deprovision(long tenantId, long robotId);
}
