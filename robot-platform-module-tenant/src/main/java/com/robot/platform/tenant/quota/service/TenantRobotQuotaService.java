package com.robot.platform.tenant.quota.service;

import com.robot.platform.tenant.quota.dal.dataobject.TenantQuotaDO;

public interface TenantRobotQuotaService {
    /** Verifies capacity while holding quota and usage rows. */
    void checkCanActivate(long tenantId);
    /** Applies an activation/deactivation delta atomically and never permits a negative count. */
    void changeRobotUsage(long tenantId, int delta);
    TenantQuotaDO getQuota(long tenantId);
    void updateRobotLimit(long tenantId, int robotLimit);
    /** Idempotently replaces derived usage with a non-negative authoritative count. */
    void reconcileRobotUsage(long tenantId, int robotUsed);
}
