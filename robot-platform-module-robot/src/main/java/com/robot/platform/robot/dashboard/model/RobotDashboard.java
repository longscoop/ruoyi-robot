package com.robot.platform.robot.dashboard.model;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

/**
 * Tenant-scoped dashboard read model. Every number is calculated from durable
 * domain facts or the Redis live projection; unsupported features are explicit.
 */
public record RobotDashboard(long totalRobots, long onlineRobots, long todayMissions,
                             long failedMissions, boolean alarmFeatureEnabled,
                             List<RobotStatus> robots, List<MissionTrend> missionTrend) {
    public record RobotStatus(long robotId, String robotCode, String name, String onlineStatus,
                              String workStatus, Integer batteryLevel, Instant lastHeartbeatAt) { }
    public record MissionTrend(LocalDate date, long total, long failed) { }
}
