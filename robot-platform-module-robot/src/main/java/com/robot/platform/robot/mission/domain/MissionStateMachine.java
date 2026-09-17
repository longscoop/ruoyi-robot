package com.robot.platform.robot.mission.domain;

import com.robot.platform.robot.mission.enums.MissionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * The only owner of lifecycle edges. Persistence code must call this before conditional updates;
 * keeping it side-effect free makes a late message incapable of changing a terminal result.
 */
@Component
public class MissionStateMachine {
    private static final Map<MissionStatus, EnumSet<MissionStatus>> EDGES = edges();

    public MissionTransitionResult transition(MissionAggregate mission, MissionStatus target,
                                              MissionTransitionContext context) {
        if (mission == null || target == null || context == null) throw new IllegalArgumentException("mission, target and context are required");
        MissionStatus source = mission.status();
        if (source.isTerminal()) throw new IllegalStateException("terminal mission is immutable: " + source);
        if (!EDGES.getOrDefault(source, EnumSet.noneOf(MissionStatus.class)).contains(target)) {
            throw new IllegalStateException("illegal mission transition: " + source + " -> " + target);
        }
        if (target == MissionStatus.FAILED && (context.errorCode() == null
                || !context.errorCode().matches("[A-Z][A-Z0-9_]{0,127}")
                || context.errorMessage() == null || context.errorMessage().isBlank() || context.errorMessage().length() > 512)) {
            throw new IllegalArgumentException("failed mission requires a stable error code and message");
        }
        if (target == MissionStatus.SUCCESS) {
            if (context.errorCode() != null || context.errorMessage() != null) {
                throw new IllegalArgumentException("successful mission cannot carry an error");
            }
            if (mission.actions().isEmpty() || mission.actions().stream()
                    .anyMatch(action -> action.required() && !"SUCCESS".equals(action.status()))) {
                throw new IllegalStateException("every required action must succeed before the mission succeeds");
            }
        }
        Instant startedAt = mission.startedAt();
        Instant finishedAt = mission.finishedAt();
        if (target == MissionStatus.RUNNING && startedAt == null) startedAt = context.occurredAt();
        if (target.isTerminal()) finishedAt = context.occurredAt();
        MissionAggregate after = mission.transitionTo(target, startedAt, finishedAt, context.errorCode(), context.errorMessage());
        return new MissionTransitionResult(mission, after, source, target);
    }

    private static Map<MissionStatus, EnumSet<MissionStatus>> edges() {
        Map<MissionStatus, EnumSet<MissionStatus>> result = new EnumMap<>(MissionStatus.class);
        result.put(MissionStatus.CREATED, EnumSet.of(MissionStatus.PENDING));
        result.put(MissionStatus.PENDING, EnumSet.of(MissionStatus.DISPATCHED, MissionStatus.FAILED, MissionStatus.CANCELLED));
        result.put(MissionStatus.DISPATCHED, EnumSet.of(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED));
        result.put(MissionStatus.RUNNING, EnumSet.of(MissionStatus.PAUSED, MissionStatus.SUCCESS, MissionStatus.FAILED, MissionStatus.CANCELLED));
        result.put(MissionStatus.PAUSED, EnumSet.of(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED));
        return Map.copyOf(result);
    }
}
