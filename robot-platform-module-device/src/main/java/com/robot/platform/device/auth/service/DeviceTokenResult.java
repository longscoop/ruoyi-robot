package com.robot.platform.device.auth.service;

import com.robot.platform.security.ApiAudience;

/** Never include a device secret in an auth response. */
public record DeviceTokenResult(String accessToken, ApiAudience audience) { }
