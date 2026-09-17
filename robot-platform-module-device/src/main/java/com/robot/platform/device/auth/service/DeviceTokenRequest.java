package com.robot.platform.device.auth.service;

/** HMAC request fields. The canonical request is built only by {@link DeviceTokenService}. */
public record DeviceTokenRequest(String deviceSn, long timestamp, String nonce, String signature) { }
