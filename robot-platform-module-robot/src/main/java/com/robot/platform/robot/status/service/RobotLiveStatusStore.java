package com.robot.platform.robot.status.service;

import com.robot.platform.robot.status.model.RobotLiveStatus;
import java.time.Duration;
import java.util.Optional;

/** All status keys are tenant-scoped; transitionOffline compares the heartbeat observed by the scanner. */
public interface RobotLiveStatusStore {
    Optional<RobotLiveStatus> get(long tenantId, long robotId);
    void put(long tenantId, long robotId, RobotLiveStatus status, Duration ttl);
    /** Restores an expired projection only while the key is still absent; a concurrent heartbeat always wins. */
    boolean restoreOfflineIfAbsent(long tenantId, long robotId, RobotLiveStatus offline, Duration ttl);
    boolean transitionOffline(long tenantId, long robotId, int expectedSchemaVersion,
                              java.time.Instant expectedLastHeartbeatAt, java.time.Instant cutoff, Duration ttl);
    static String key(long tenantId, long robotId) { return "tenant:" + tenantId + ":robot:" + robotId + ":status"; }
}
