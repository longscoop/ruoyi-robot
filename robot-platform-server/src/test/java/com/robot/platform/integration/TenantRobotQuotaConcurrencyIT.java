package com.robot.platform.integration;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Real InnoDB row-lock proof for two competing device activations. */
class TenantRobotQuotaConcurrencyIT extends AbstractRobotPlatformIntegrationTest {

    @Autowired
    private TenantRobotQuotaService quotaService;
    @Autowired
    private TenantUsageMapper usageMapper;

    @Test
    void concurrentActivationsAtLimitOneProduceOneSuccessAndUsageOne() throws Exception {
        long tenantId = 303L;
        quotaService.updateRobotLimit(tenantId, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> changeUsageAfterStart(tenantId, ready, start));
            var second = executor.submit(() -> changeUsageAfterStart(tenantId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(usageMapper.selectByTenantId(tenantId).getRobotUsed()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private boolean changeUsageAfterStart(long tenantId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("activation start barrier timed out");
        }
        try {
            quotaService.changeRobotUsage(tenantId, 1);
            return true;
        } catch (ServiceException ignored) {
            return false;
        }
    }
}
