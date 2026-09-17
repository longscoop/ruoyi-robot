package com.robot.platform.robot.mission;

import com.robot.platform.robot.mission.domain.MissionAggregate;
import com.robot.platform.robot.mission.domain.MissionStateMachine;
import com.robot.platform.robot.mission.domain.MissionActionState;
import com.robot.platform.robot.mission.domain.MissionTransitionContext;
import com.robot.platform.robot.mission.enums.MissionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.Stream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionStateMachineTest {

    private final MissionStateMachine machine = new MissionStateMachine();

    @Test
    void permitsOnlyDeclaredLifecycleEdges() {
        Stream.of(
                edge(MissionStatus.CREATED, MissionStatus.PENDING),
                edge(MissionStatus.PENDING, MissionStatus.DISPATCHED),
                edge(MissionStatus.PENDING, MissionStatus.FAILED),
                edge(MissionStatus.PENDING, MissionStatus.CANCELLED),
                edge(MissionStatus.DISPATCHED, MissionStatus.RUNNING),
                edge(MissionStatus.DISPATCHED, MissionStatus.FAILED),
                edge(MissionStatus.DISPATCHED, MissionStatus.CANCELLED),
                edge(MissionStatus.RUNNING, MissionStatus.PAUSED),
                edge(MissionStatus.RUNNING, MissionStatus.SUCCESS),
                edge(MissionStatus.RUNNING, MissionStatus.FAILED),
                edge(MissionStatus.RUNNING, MissionStatus.CANCELLED),
                edge(MissionStatus.PAUSED, MissionStatus.RUNNING),
                edge(MissionStatus.PAUSED, MissionStatus.FAILED),
                edge(MissionStatus.PAUSED, MissionStatus.CANCELLED)
        ).forEach(edge -> assertThat(machine.transition(mission(edge.from()), edge.to(),
                        edge.to() == MissionStatus.FAILED ? failureContext() : context()).after().status())
                .as("%s -> %s", edge.from(), edge.to()).isEqualTo(edge.to()));
    }

    @Test
    void rejectsTerminalMissionMutation() {
        for (MissionStatus terminal : Stream.of(MissionStatus.SUCCESS, MissionStatus.FAILED, MissionStatus.CANCELLED).toList()) {
            for (MissionStatus target : MissionStatus.values()) {
                assertThatThrownBy(() -> machine.transition(mission(terminal), target, context()))
                        .as("terminal %s cannot become %s", terminal, target).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test
    void stampsStartedAndFinishedAtTransitionBoundaries() {
        MissionAggregate dispatched = machine.transition(mission(MissionStatus.PENDING), MissionStatus.DISPATCHED, context()).after();
        MissionAggregate running = machine.transition(dispatched, MissionStatus.RUNNING, context()).after();
        MissionAggregate success = machine.transition(running, MissionStatus.SUCCESS, context()).after();

        assertThat(running.startedAt()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(success.finishedAt()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
    }

    @Test
    void failedRequiresAStableTerminalError() {
        assertThatThrownBy(() -> machine.transition(mission(MissionStatus.PENDING), MissionStatus.FAILED,
                new MissionTransitionContext(Instant.parse("2026-09-14T00:00:00Z"), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(machine.transition(mission(MissionStatus.PENDING), MissionStatus.FAILED,
                new MissionTransitionContext(Instant.parse("2026-09-14T00:00:00Z"), "MISSION_WAIT_TIMEOUT", "expired"))
                .after().status()).isEqualTo(MissionStatus.FAILED);
    }

    @Test
    void failedRejectsBlankOrMalformedErrorsAndRetainsValidErrorFacts() {
        for (String code : List.of("", " ", "not a code", "X".repeat(129))) {
            assertThatThrownBy(() -> machine.transition(mission(MissionStatus.PENDING), MissionStatus.FAILED,
                    new MissionTransitionContext(context().occurredAt(), code, "failed")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> machine.transition(mission(MissionStatus.PENDING), MissionStatus.FAILED,
                new MissionTransitionContext(context().occurredAt(), "MISSION_WAIT_TIMEOUT", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        MissionAggregate failed = machine.transition(mission(MissionStatus.PENDING), MissionStatus.FAILED, failureContext()).after();
        assertThat(failed.errorCode()).isEqualTo("MISSION_WAIT_TIMEOUT");
        assertThat(failed.errorMessage()).isEqualTo("expired");
    }

    @Test
    void successCannotBypassValidationWithMissingActionsOrRequiredSkippedAction() {
        for (List<MissionActionState> actions : List.of(List.<MissionActionState>of(),
                List.of(new MissionActionState(1L, "SKIPPED", true)))) {
            assertThatThrownBy(() -> machine.transition(mission(MissionStatus.RUNNING, actions), MissionStatus.SUCCESS, context()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void successRequiresEveryRequiredActionToSucceedAndHasNoError() {
        MissionAggregate incomplete = mission(MissionStatus.RUNNING, List.of(new MissionActionState(1L, "SUCCESS", true),
                new MissionActionState(2L, "RUNNING", true)));
        assertThatThrownBy(() -> machine.transition(incomplete, MissionStatus.SUCCESS, context()))
                .isInstanceOf(IllegalStateException.class);
        MissionAggregate complete = mission(MissionStatus.RUNNING, List.of(new MissionActionState(1L, "SUCCESS", true),
                new MissionActionState(2L, "SKIPPED", false)));
        assertThatThrownBy(() -> machine.transition(complete, MissionStatus.SUCCESS,
                new MissionTransitionContext(Instant.parse("2026-09-14T00:00:00Z"), "NOT_ALLOWED", "error")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(machine.transition(complete, MissionStatus.SUCCESS, context()).after().status()).isEqualTo(MissionStatus.SUCCESS);
    }

    private static MissionAggregate mission(MissionStatus status) {
        return mission(status, List.of(new MissionActionState(1L, "SUCCESS", true)));
    }

    private static MissionAggregate mission(MissionStatus status, List<MissionActionState> actions) {
        return new MissionAggregate(1L, 10L, 20L, status, 3, null, null, 0, actions);
    }

    private static MissionTransitionContext context() {
        return new MissionTransitionContext(Instant.parse("2026-09-14T00:00:00Z"), null, null);
    }

    private static MissionTransitionContext failureContext() {
        return new MissionTransitionContext(context().occurredAt(), "MISSION_WAIT_TIMEOUT", "expired");
    }

    private static Edge edge(MissionStatus from, MissionStatus to) {
        return new Edge(from, to);
    }

    private record Edge(MissionStatus from, MissionStatus to) {
    }
}
