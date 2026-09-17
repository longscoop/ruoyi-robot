package com.robot.platform.tenant.quota.service;

import com.robot.platform.tenant.quota.dal.dataobject.TenantQuotaDO;
import com.robot.platform.tenant.quota.dal.dataobject.TenantUsageDO;
import com.robot.platform.tenant.quota.dal.mysql.TenantQuotaMapper;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.tenant.quota.enums.TenantQuotaErrorCodeConstants.*;

@Service
public class TenantRobotQuotaServiceImpl implements TenantRobotQuotaService {

    private final TenantQuotaMapper quotaMapper;
    private final TenantUsageMapper usageMapper;

    public TenantRobotQuotaServiceImpl(TenantQuotaMapper quotaMapper, TenantUsageMapper usageMapper) {
        this.quotaMapper = quotaMapper;
        this.usageMapper = usageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkCanActivate(long tenantId) {
        LockedQuota quota = lockQuotaAndUsage(tenantId);
        if (quota.usage().getRobotUsed() >= quota.quota().getRobotLimit()) {
            throw exception(ROBOT_QUOTA_EXHAUSTED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeRobotUsage(long tenantId, int delta) {
        LockedQuota quota = lockQuotaAndUsage(tenantId);
        int nextUsage = quota.usage().getRobotUsed() + delta;
        if (nextUsage < 0) {
            throw exception(ROBOT_USAGE_NEGATIVE);
        }
        // Recheck under the same locks: a separate checkCanActivate call cannot race this update.
        if (nextUsage > quota.quota().getRobotLimit()) {
            throw exception(ROBOT_QUOTA_EXHAUSTED);
        }
        quota.usage().setRobotUsed(nextUsage);
        usageMapper.updateById(quota.usage());
    }

    @Override
    public TenantQuotaDO getQuota(long tenantId) {
        TenantQuotaDO quota = quotaMapper.selectByTenantId(tenantId);
        if (quota == null) {
            throw exception(TENANT_QUOTA_NOT_EXISTS);
        }
        return quota;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRobotLimit(long tenantId, int robotLimit) {
        if (robotLimit < 0) {
            throw exception(ROBOT_USAGE_NEGATIVE);
        }
        // INSERT ... ON DUPLICATE KEY makes initial provisioning race-safe, then normal locks apply.
        quotaMapper.insertIfAbsent(tenantId);
        usageMapper.insertIfAbsent(tenantId);
        LockedQuota lockedQuota = lockQuotaAndUsage(tenantId);
        TenantQuotaDO quota = lockedQuota.quota();
        TenantUsageDO usage = lockedQuota.usage();
        if (robotLimit < usage.getRobotUsed()) {
            throw exception(ROBOT_LIMIT_BELOW_USAGE);
        }
        quota.setRobotLimit(robotLimit);
        quotaMapper.updateById(quota);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileRobotUsage(long tenantId, int robotUsed) {
        if (robotUsed < 0) {
            throw exception(ROBOT_USAGE_NEGATIVE);
        }
        // Lock quota first for a stable lock order with activation and deactivation.
        TenantQuotaDO quota = quotaMapper.selectByTenantIdForUpdate(tenantId);
        if (quota == null) {
            throw exception(TENANT_QUOTA_NOT_EXISTS);
        }
        TenantUsageDO usage = requireUsageForUpdate(tenantId);
        usage.setRobotUsed(robotUsed);
        usageMapper.updateById(usage);
    }

    private LockedQuota lockQuotaAndUsage(long tenantId) {
        TenantQuotaDO quota = quotaMapper.selectByTenantIdForUpdate(tenantId);
        if (quota == null) {
            throw exception(TENANT_QUOTA_NOT_EXISTS);
        }
        return new LockedQuota(quota, requireUsageForUpdate(tenantId));
    }

    private TenantUsageDO requireUsageForUpdate(long tenantId) {
        TenantUsageDO usage = usageMapper.selectByTenantIdForUpdate(tenantId);
        if (usage == null) {
            throw exception(TENANT_USAGE_NOT_EXISTS);
        }
        return usage;
    }

    private record LockedQuota(TenantQuotaDO quota, TenantUsageDO usage) {
    }
}
