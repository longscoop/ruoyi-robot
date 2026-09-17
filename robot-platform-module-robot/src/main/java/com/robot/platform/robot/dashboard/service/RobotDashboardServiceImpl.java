package com.robot.platform.robot.dashboard.service;

import com.robot.platform.robot.dashboard.model.RobotDashboard;
import com.robot.platform.robot.mission.dal.dataobject.MissionDashboardTrendDO;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only aggregation service; it never substitutes tenant context with client input. */
@Service
public class RobotDashboardServiceImpl implements RobotDashboardService {
    private static final int STATUS_ROWS_LIMIT = 20;

    private final RobotMapper robots;
    private final MissionMapper missions;
    private final RobotLiveStatusQueryService liveStatuses;
    private final Clock robotHeartbeatClock;
    private final ZoneId businessZoneId;

    /** The zone is configurable because MySQL timestamps represent tenant operating-day facts. */
    @Autowired
    public RobotDashboardServiceImpl(RobotMapper robots, MissionMapper missions,
                                     RobotLiveStatusQueryService liveStatuses, Clock clock,
                                     @Value("${robot.dashboard.zone-id:Asia/Shanghai}") String businessZoneId) {
        this(robots, missions, liveStatuses, clock, ZoneId.of(businessZoneId));
    }

    /** Focused unit-test constructor with deterministic time and business zone. */
    public RobotDashboardServiceImpl(RobotMapper robots, MissionMapper missions,
                                     RobotLiveStatusQueryService liveStatuses, Clock clock, ZoneId businessZoneId) {
        this.robots = robots;
        this.missions = missions;
        this.liveStatuses = liveStatuses;
        this.robotHeartbeatClock = clock;
        this.businessZoneId = businessZoneId;
    }

    @Override
    public RobotDashboard getDashboard(long tenantId) {
        if (tenantId <= 0) throw new IllegalArgumentException("tenantId must be positive");
        LocalDate today = LocalDate.now(robotHeartbeatClock.withZone(businessZoneId));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<RobotDO> tenantRobots = robots.selectDashboardRobots(tenantId);
        // Read the Redis-backed projection once per robot. The query service repairs stale
        // projections and falls back to the durable snapshot if Redis is unavailable.
        Map<Long, RobotLiveStatus> liveByRobotId = tenantRobots.stream().collect(Collectors.toMap(RobotDO::getId,
                robot -> liveStatuses.find(tenantId, robot.getId()).orElse(null)));
        long online = liveByRobotId.values().stream()
                .filter(status -> status != null && "ONLINE".equals(status.onlineStatus().name()))
                .count();
        List<RobotDashboard.RobotStatus> statuses = tenantRobots.stream().limit(STATUS_ROWS_LIMIT)
                .map(robot -> toStatus(robot, liveByRobotId.get(robot.getId()))).toList();
        List<RobotDashboard.MissionTrend> trend = missions.selectDashboardTrend(tenantId, today.minusDays(6), today)
                .stream().map(this::toTrend).toList();
        return new RobotDashboard(robots.countDashboardRobots(tenantId), online, missions.countCreatedBetween(tenantId, start, end),
                missions.countFailedBetween(tenantId, start, end), false, statuses, trend);
    }

    private RobotDashboard.RobotStatus toStatus(RobotDO robot, RobotLiveStatus live) {
        return new RobotDashboard.RobotStatus(robot.getId(), robot.getRobotCode(), robot.getName(),
                live == null ? robot.getOnlineStatus() : live.onlineStatus().name(),
                live == null ? robot.getWorkStatus() : live.workStatus().name(),
                live == null ? robot.getBatteryLevel() : live.batteryLevel(),
                live == null ? null : live.lastHeartbeatAt());
    }

    private RobotDashboard.MissionTrend toTrend(MissionDashboardTrendDO row) {
        return new RobotDashboard.MissionTrend(row.getMissionDate(), row.getTotal(), row.getFailed());
    }
}
