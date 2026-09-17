package com.robot.platform.device.product.service;

import com.robot.platform.framework.security.core.service.SecurityFrameworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Isolates the upstream security expression from domain authorization rules. */
@Component
@RequiredArgsConstructor
public class ProductAccessPolicy {

    private static final String PLATFORM_SUPER_ADMIN = "super_admin";

    private final SecurityFrameworkService securityFrameworkService;

    public boolean isPlatformSuperAdmin() {
        return securityFrameworkService.hasRole(PLATFORM_SUPER_ADMIN);
    }
}
