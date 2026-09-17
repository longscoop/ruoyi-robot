package com.robot.platform.robot.mission.domain;

import com.robot.platform.robot.mission.enums.MissionStatus;

/** Result keeps before/after values available to event persistence without mutable aggregate state. */
public record MissionTransitionResult(MissionAggregate before, MissionAggregate after,
                                      MissionStatus from, MissionStatus to) {
}
