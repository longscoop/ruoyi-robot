package com.robot.platform.device.device.service;

import com.robot.platform.framework.security.core.service.SecurityFrameworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceAccessPolicy {
    private final SecurityFrameworkService securityFrameworkService;
    public boolean isPlatformSuperAdmin() { return securityFrameworkService.hasRole("super_admin"); }
}
