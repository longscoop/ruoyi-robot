package com.robot.platform.tenant.quota;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.robot.platform.tenant.quota.dal.dataobject.TenantQuotaDO;
import com.robot.platform.tenant.quota.dal.dataobject.TenantUsageDO;
import com.robot.platform.tenant.quota.dal.mysql.TenantQuotaMapper;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TenantRobotQuotaServiceTest {

    private final TenantQuotaMapper quotaMapper = mock(TenantQuotaMapper.class);
    private final TenantUsageMapper usageMapper = mock(TenantUsageMapper.class);
    private final TenantRobotQuotaService service = new TenantRobotQuotaServiceImpl(quotaMapper, usageMapper);

    @Test
    void rejectsActivationAtRobotLimit() {
        lockRows(quota(2), usage(2));

        assertThatThrownBy(() -> service.checkCanActivate(10L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("机器人配额已用尽");
    }

    @Test
    void incrementsUsageWhenCapacityRemains() {
        TenantUsageDO usage = usage(0);
        lockRows(quota(2), usage);

        service.changeRobotUsage(10L, 1);

        assertThat(usage.getRobotUsed()).isEqualTo(1);
        verify(usageMapper).updateById(org.mockito.ArgumentMatchers.<TenantUsageDO>argThat(row -> row.getRobotUsed() == 1));
    }

    @Test
    void rejectsUsageIncrementPastLimit() {
        lockRows(quota(1), usage(1));

        assertThatThrownBy(() -> service.changeRobotUsage(10L, 1)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("机器人配额已用尽");
        verify(usageMapper, never()).updateById(any(TenantUsageDO.class));
    }

    @Test
    void rejectsUsageChangeThatWouldBecomeNegative() {
        lockRows(quota(2), usage(0));

        assertThatThrownBy(() -> service.changeRobotUsage(10L, -1)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("机器人用量不能为负数");
        verify(usageMapper, never()).updateById(any(TenantUsageDO.class));
    }

    @Test
    void provisionsMissingRowsBeforeUpdatingLimit() {
        TenantQuotaDO quota = quota(0);
        lockRows(quota, usage(0));

        service.updateRobotLimit(10L, 2);

        verify(quotaMapper).insertIfAbsent(10L);
        verify(usageMapper).insertIfAbsent(10L);
        assertThat(quota.getRobotLimit()).isEqualTo(2);
        verify(quotaMapper).updateById(org.mockito.ArgumentMatchers.<TenantQuotaDO>argThat(row -> row.getRobotLimit() == 2));
    }

    @Test
    void rejectsLimitBelowCurrentUsage() {
        lockRows(quota(3), usage(2));

        assertThatThrownBy(() -> service.updateRobotLimit(10L, 1)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("机器人配额不能低于当前用量");
        verify(quotaMapper, never()).updateById(any(TenantQuotaDO.class));
    }

    @Test
    void reconciliationIsIdempotentForSameAuthoritativeUsage() {
        TenantUsageDO usage = usage(0);
        lockRows(quota(2), usage);

        service.reconcileRobotUsage(10L, 1);
        service.reconcileRobotUsage(10L, 1);

        assertThat(usage.getRobotUsed()).isEqualTo(1);
        verify(usageMapper, times(2)).updateById(org.mockito.ArgumentMatchers.<TenantUsageDO>argThat(row -> row.getRobotUsed() == 1));
    }

    private void lockRows(TenantQuotaDO quota, TenantUsageDO usage) {
        when(quotaMapper.selectByTenantIdForUpdate(10L)).thenReturn(quota);
        when(usageMapper.selectByTenantIdForUpdate(10L)).thenReturn(usage);
    }

    private static TenantQuotaDO quota(int robotLimit) {
        return TenantQuotaDO.builder().id(1L).tenantId(10L).robotLimit(robotLimit).build();
    }

    private static TenantUsageDO usage(int robotUsed) {
        return TenantUsageDO.builder().id(1L).tenantId(10L).robotUsed(robotUsed).build();
    }
}
