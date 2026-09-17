package com.robot.platform.device.identity.service;

/** Safe machine identity, deliberately excluding every credential material. */
public record DeviceIdentity(long deviceId, long tenantId, long productId, String deviceSn,
                             String mqttUsername, int credentialVersion) { }
