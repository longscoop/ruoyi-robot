package com.robot.platform.device.spi;
public record RobotProvisionCommand(long tenantId, long deviceId, long productId, String robotCode, String robotName) { }
