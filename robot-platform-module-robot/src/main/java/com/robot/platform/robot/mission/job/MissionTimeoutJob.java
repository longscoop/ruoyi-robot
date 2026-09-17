package com.robot.platform.robot.mission.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.robot.platform.robot.mission.dal.dataobject.MissionDO;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.service.MissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Timeout does not blindly overwrite state: MissionService's versioned transition resolves the cancel race. */
@Component
public class MissionTimeoutJob {
    private final MissionMapper missions;
    private final MissionService service;
    private final Clock clock;
    private final Duration pendingTimeout;

    public MissionTimeoutJob(MissionMapper missions, MissionService service, Clock clock,
                             @Value("${robot.mission.pending-timeout:PT10M}") Duration pendingTimeout) {
        this.missions = missions; this.service = service; this.clock = clock; this.pendingTimeout = pendingTimeout;
        if (pendingTimeout == null || pendingTimeout.isNegative() || pendingTimeout.isZero()) {
            throw new IllegalArgumentException("mission pending timeout must be positive");
        }
    }

    @Scheduled(fixedDelayString = "${robot.mission.timeout-scan-interval:PT30S}")
    public void scan() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant().minus(pendingTimeout), ZoneOffset.UTC);
        for (MissionDO mission : missions.selectExpiredPendingIgnoringTenant(cutoff)) {
            TenantUtils.execute(mission.getTenantId(), () -> service.timeoutPending(mission.getId()));
        }
    }
}
