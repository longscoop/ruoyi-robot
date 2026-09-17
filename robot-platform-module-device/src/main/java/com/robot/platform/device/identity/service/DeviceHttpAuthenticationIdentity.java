package com.robot.platform.device.identity.service;

/** Server-only projection used to verify device HTTP HMAC credentials; never serialize or log it. */
public record DeviceHttpAuthenticationIdentity(long deviceId, long tenantId, long robotId, String deviceSn,
                                                int credentialVersion, String httpSecretCiphertext) { }
