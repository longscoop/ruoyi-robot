package com.robot.platform.device.identity.service;

import com.robot.platform.device.device.service.DeviceActivationResult;
import com.robot.platform.device.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceCredentialServiceImpl implements DeviceCredentialService {
    private final DeviceService deviceService;
    @Override public DeviceActivationResult rotate(long deviceId) { return deviceService.rotateCredentials(deviceId); }
}
