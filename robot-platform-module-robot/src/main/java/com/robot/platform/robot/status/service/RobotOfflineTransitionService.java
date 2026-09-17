package com.robot.platform.robot.status.service;

import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Commits the durable state and its event together; losing Redis NX rolls both back. */
@Service
@Slf4j
public class RobotOfflineTransitionService {
    private final RobotMapper robots;
    private final RobotLiveStatusStore statuses;
    private final RobotRealtimeEventPublisher events;

    public RobotOfflineTransitionService(RobotMapper robots, RobotLiveStatusStore statuses,
                                         RobotRealtimeEventPublisher events) {
        this.robots = robots;
        this.statuses = statuses;
        this.events = events;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean persist(RobotDO candidate, Instant cutoff, Instant now, Duration statusTtl) {
        // This is deliberately the first mutable-state operation. Heartbeats acquire this exact
        // row lock before touching Redis too, establishing one DB -> Redis lock order.
        RobotDO robot = robots.selectByTenantAndIdForUpdate(candidate.getTenantId(), candidate.getId());
        if (robot == null || !RobotOnlineStatus.ONLINE.name().equals(robot.getOnlineStatus())
                || (robot.getLastHeartbeatTime() != null
                && robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC).isAfter(cutoff))) {
            return false;
        }
        Optional<RobotLiveStatus> live = Optional.empty();
        boolean redisAvailable = true;
        try {
            live = statuses.get(robot.getTenantId(), robot.getId());
        } catch (RuntimeException redisFailure) {
            // MySQL remains authoritative. Status reads reconcile under the same row lock;
            // they retry Redis repair even though this OFFLINE row is no longer a scan candidate.
            redisAvailable = false;
            log.warn("[persist][Redis status unavailable; committing durable offline state tenantId({}) robotId({})]",
                    robot.getTenantId(), robot.getId(), redisFailure);
        }
        RobotLiveStatus observed = live.orElseGet(() -> fromSnapshot(robot));
        if (live.isPresent() && observed.onlineStatus() == RobotOnlineStatus.ONLINE) {
            if (observed.lastHeartbeatAt() == null || observed.lastHeartbeatAt().isAfter(cutoff)
                    || !statuses.transitionOffline(robot.getTenantId(), robot.getId(), observed.schemaVersion(),
                    observed.lastHeartbeatAt(), cutoff, statusTtl)) {
                return false;
            }
        } else if (redisAvailable && live.isEmpty()) {
            RobotLiveStatus offline = observed.withOnlineStatus(RobotOnlineStatus.OFFLINE);
            try {
                if (!statuses.restoreOfflineIfAbsent(robot.getTenantId(), robot.getId(), offline, statusTtl)) {
                    throw new ConcurrentHeartbeatProjectionException();
                }
            } catch (ConcurrentHeartbeatProjectionException heartbeatWon) {
                throw heartbeatWon;
            } catch (RuntimeException redisFailure) {
                log.warn("[persist][DB remains authoritative; Redis projection restore will recover from DB tenantId({}) robotId({})]",
                        robot.getTenantId(), robot.getId(), redisFailure);
            }
        }
        int changed = robots.markOfflineIfHeartbeatBefore(robot.getTenantId(), robot.getId(),
                LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC));
        if (changed == 0) return false;
        RobotLiveStatus offline = observed.withOnlineStatus(RobotOnlineStatus.OFFLINE);
        events.publish(new TenantRobotRealtimeEvent(1, robot.getTenantId(), robot.getId(),
                "OFFLINE-" + robot.getId() + "-" + cutoff.toEpochMilli(), "ROBOT_STATUS_CHANGED", now, offline,
                "OFFLINE-" + robot.getId() + "-" + observed.lastHeartbeatEpochMillis()));
        return true;
    }

    private static RobotLiveStatus fromSnapshot(RobotDO robot) {
        Instant heartbeat = robot.getLastHeartbeatTime() == null ? null
                : robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC);
        return new RobotLiveStatus(1, RobotOnlineStatus.ONLINE,
                com.robot.platform.robot.robot.enums.RobotWorkStatus.valueOf(robot.getWorkStatus()),
                robot.getBatteryLevel(), 0, 0, 0, robot.getIpAddress(), robot.getCurrentMissionId(),
                robot.getSoftwareVersion(), heartbeat, heartbeat == null ? 0 : heartbeat.toEpochMilli());
    }
}
