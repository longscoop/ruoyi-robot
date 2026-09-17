package com.robot.platform.robot.status.model;

import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import java.time.Instant;

/** Versionable Redis projection; it contains no authority or credential material. */
public record RobotLiveStatus(int schemaVersion, RobotOnlineStatus onlineStatus, RobotWorkStatus workStatus,
                              Integer batteryLevel, int cpuUsage, int memoryUsage, int temperatureCelsius,
                              String ipAddress, String currentMissionId, String softwareVersion, Instant lastHeartbeatAt,
                              long lastHeartbeatEpochMillis) {
    public RobotLiveStatus(RobotOnlineStatus onlineStatus, RobotWorkStatus workStatus, Integer batteryLevel) {
        this(1, onlineStatus, workStatus, batteryLevel, 0, 0, 0, null, null, null, null, 0);
    }

    public RobotLiveStatus withOnlineStatus(RobotOnlineStatus value) {
        return new RobotLiveStatus(schemaVersion, value, workStatus, batteryLevel, cpuUsage, memoryUsage,
                temperatureCelsius, ipAddress, currentMissionId, softwareVersion, lastHeartbeatAt,
                lastHeartbeatEpochMillis);
    }
}
