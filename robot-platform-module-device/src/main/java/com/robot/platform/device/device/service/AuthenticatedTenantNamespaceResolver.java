package com.robot.platform.device.device.service;

import com.robot.platform.framework.common.biz.system.tenant.TenantCommonApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedTenantNamespaceResolver implements TenantNamespaceResolver {
    private final TenantCommonApi tenantCommonApi;
    @Override public String resolve(long tenantId) {
        tenantCommonApi.validateTenant(tenantId);
        return "t-" + tenantId;
    }
}
