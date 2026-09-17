package com.robot.platform.robot.status.controller.device.vo;

/** Minimal device configuration; deliberately separate from the volatile live-status projection. */
public record DeviceRobotConfigRespVO(long robotId, int heartbeatIntervalSeconds) { }
