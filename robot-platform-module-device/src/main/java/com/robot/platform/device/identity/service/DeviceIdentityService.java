package com.robot.platform.device.identity.service;

public interface DeviceIdentityService {
    DeviceIdentity findBySn(String deviceSn);
    DeviceHttpAuthenticationIdentity findForHttpAuthentication(String deviceSn);
}
