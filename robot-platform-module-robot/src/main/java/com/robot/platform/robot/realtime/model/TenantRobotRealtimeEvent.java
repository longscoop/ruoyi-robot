package com.robot.platform.robot.realtime.model;

import com.robot.platform.robot.status.model.RobotLiveStatus;

import java.time.Instant;

/** Stable tenant event schema carrying the complete status projection used by every transport. */
public record TenantRobotRealtimeEvent(int schemaVersion, long tenantId, long robotId, String requestId,
                                       String type, Instant occurredAt, RobotLiveStatus data, String eventId) {
    public TenantRobotRealtimeEvent(int schemaVersion, long tenantId, long robotId, String requestId,
                                   String type, Instant occurredAt, RobotLiveStatus data) {
        this(schemaVersion, tenantId, robotId, requestId, type, occurredAt, data, requestId);
    }

    public TenantRobotRealtimeEvent {
        if (schemaVersion < 1 || tenantId <= 0 || robotId <= 0 || requestId == null || requestId.isBlank()
                || type == null || type.isBlank() || occurredAt == null || data == null
                || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("tenant robot event requires routing identity");
        }
    }
}
