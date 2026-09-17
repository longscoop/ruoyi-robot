package com.robot.platform.device.device.service;
public record DeviceCredentialsRevokedEvent(long deviceId, long tenantId, int credentialVersion, String reason) { }
