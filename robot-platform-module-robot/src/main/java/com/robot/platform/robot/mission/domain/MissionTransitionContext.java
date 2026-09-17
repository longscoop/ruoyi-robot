package com.robot.platform.robot.mission.domain;

import java.time.Instant;

/** Transition input intentionally excludes mappers and transport so every caller shares the same rules. */
public record MissionTransitionContext(Instant occurredAt, String errorCode, String errorMessage) {
    public MissionTransitionContext {
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
}
