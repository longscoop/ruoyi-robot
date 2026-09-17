package com.robot.platform.robot.status.job;

import com.robot.platform.framework.tenant.core.util.TenantUtils;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import com.robot.platform.robot.status.service.ConcurrentHeartbeatProjectionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import com.robot.platform.robot.status.service.RobotOfflineTransitionService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

/** Both writers lock the robot row before Redis revalidation, then the conditional MySQL update is durable. */
@Component
@Slf4j
public class RobotOfflineJob {
    private final RobotMapper robots;
    private final RobotLiveStatusStore statuses;
    private final Duration statusTtl;
    private final Duration offlineTimeout;
    private final RobotOfflineTransitionService transitions;
    private final Clock clock;
    @Autowired public RobotOfflineJob(RobotMapper robots, RobotLiveStatusStore statuses,
                           @Value("${robot.heartbeat.status-ttl:PT10M}") Duration statusTtl,
                           @Value("${robot.heartbeat.offline-timeout:PT2M}") Duration offlineTimeout,
                           RobotOfflineTransitionService transitions, Clock clock) {
        this.robots = robots; this.statuses = statuses; this.statusTtl = statusTtl; this.offlineTimeout = offlineTimeout;
        this.transitions = transitions; this.clock = clock;
        if (statusTtl == null || offlineTimeout == null || statusTtl.compareTo(offlineTimeout) <= 0) {
            throw new IllegalArgumentException("status TTL must exceed offline timeout");
        }
    }

    @Scheduled(fixedDelayString = "${robot.heartbeat.offline-scan-interval:PT30S}")
    public void scan() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(offlineTimeout);
        for (RobotDO robot : robots.selectOnlineCandidatesIgnoringTenant()) {
            TenantUtils.execute(robot.getTenantId(), () -> scanRobot(robot, cutoff, now));
        }
    }

    private void scanRobot(RobotDO robot, Instant cutoff, Instant now) {
        try {
            transitions.persist(robot, cutoff, now, statusTtl);
        } catch (ConcurrentHeartbeatProjectionException heartbeatWon) {
            // A SETNX/CAS failure means a newer heartbeat won; do not publish an offline event.
            log.debug("[scanRobot][Concurrent heartbeat won Redis revalidation tenantId({}) robotId({})]",
                    robot.getTenantId(), robot.getId());
        }
    }
}
