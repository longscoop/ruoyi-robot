package com.robot.platform.robot.status.service;

import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.robot.service.RobotQueryService;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

/** Read contract for all audiences: Redis is a projection; MySQL remains the recovery snapshot. */
@Service
@Slf4j
public class RobotLiveStatusQueryService implements RobotQueryService {
    private final RobotLiveStatusStore statuses;
    private final RobotMapper robots;
    private final Duration statusTtl;

    @Autowired
    public RobotLiveStatusQueryService(RobotLiveStatusStore statuses, RobotMapper robots,
                                       @Qualifier("robotHeartbeatStatusTtl") Duration statusTtl) {
        this.statuses = statuses;
        this.robots = robots;
        this.statusTtl = statusTtl;
    }

    /** Retained for focused unit tests and callers that only need the default projection lifetime. */
    public RobotLiveStatusQueryService(RobotLiveStatusStore statuses, RobotMapper robots) {
        this(statuses, robots, Duration.ofMinutes(10));
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<RobotLiveStatus> find(long tenantId, long robotId) {
        // Use the heartbeat/offline DB -> Redis lock order. Redis timestamps can be newer than
        // a throttled DB snapshot even after OFFLINE commits, so timestamps alone cannot prove
        // an in-flight heartbeat. Waiting for its row lock makes the committed state decisive.
        RobotDO robot = robots.selectByTenantAndIdForUpdate(tenantId, robotId);
        if (robot == null) return Optional.empty();
        Optional<RobotLiveStatus> live;
        try {
            live = statuses.get(tenantId, robotId);
        } catch (RuntimeException redisFailure) {
            log.warn("[find][Redis status unavailable; using DB fallback tenantId({}) robotId({})]",
                    tenantId, robotId, redisFailure);
            live = Optional.empty();
        }
        if (live.isPresent()) {
            RobotLiveStatus observed = live.orElseThrow();
            if (RobotOnlineStatus.OFFLINE.name().equals(robot.getOnlineStatus())
                    && observed.onlineStatus() == RobotOnlineStatus.ONLINE) {
                repairOfflineProjection(tenantId, robotId, observed);
                return Optional.of(observed.withOnlineStatus(RobotOnlineStatus.OFFLINE));
            }
            return live;
        }
        return Optional.of(fromSnapshot(robot));
    }

    private void repairOfflineProjection(long tenantId, long robotId, RobotLiveStatus observed) {
        try {
            // CAS preserves telemetry and also guards against a replacement Redis projection.
            // On failure, the durable OFFLINE row corrects this response and retries on each read.
            if (observed.lastHeartbeatAt() != null) {
                statuses.transitionOffline(tenantId, robotId, observed.schemaVersion(),
                        observed.lastHeartbeatAt(), observed.lastHeartbeatAt(), statusTtl);
            }
        } catch (RuntimeException redisFailure) {
            log.warn("[find][Failed to repair stale ONLINE Redis projection tenantId({}) robotId({})]",
                    tenantId, robotId, redisFailure);
        }
    }

    private static RobotLiveStatus fromSnapshot(RobotDO robot) {
        return new RobotLiveStatus(1, RobotOnlineStatus.valueOf(robot.getOnlineStatus()),
                RobotWorkStatus.valueOf(robot.getWorkStatus()), robot.getBatteryLevel(),
                0, 0, 0, robot.getIpAddress(), robot.getCurrentMissionId(), robot.getSoftwareVersion(),
                robot.getLastHeartbeatTime() == null ? null : robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC),
                robot.getLastHeartbeatTime() == null ? 0 : robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RobotLiveStatus getStatus(long robotId) {
        return find(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(), robotId)
                .orElse(null);
    }
}
