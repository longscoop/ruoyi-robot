package com.robot.platform.robot.mission.domain;

import com.robot.platform.robot.mission.enums.MissionStatus;

import java.time.Instant;
import java.util.List;

/** Immutable domain view used to validate lifecycle changes without infrastructure coupling. */
public record MissionAggregate(Long id, Long tenantId, Long robotId, MissionStatus status, int priority,
                               Instant startedAt, Instant finishedAt, int version, List<MissionActionState> actions,
                               String errorCode, String errorMessage) {
    public MissionAggregate {
        actions = List.copyOf(actions);
    }

    public MissionAggregate(Long id, Long tenantId, Long robotId, MissionStatus status, int priority,
                            Instant startedAt, Instant finishedAt, int version) {
        this(id, tenantId, robotId, status, priority, startedAt, finishedAt, version, List.of(), null, null);
    }

    public MissionAggregate(Long id, Long tenantId, Long robotId, MissionStatus status, int priority,
                            Instant startedAt, Instant finishedAt, int version, List<MissionActionState> actions) {
        this(id, tenantId, robotId, status, priority, startedAt, finishedAt, version, actions, null, null);
    }
    MissionAggregate transitionTo(MissionStatus next, Instant startedAt, Instant finishedAt,
                                  String errorCode, String errorMessage) {
        return new MissionAggregate(id, tenantId, robotId, next, priority, startedAt, finishedAt, version + 1,
                actions, errorCode, errorMessage);
    }
}
