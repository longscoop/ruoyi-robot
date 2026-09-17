package com.robot.platform.robot.mission.enums;

/** Durable mission lifecycle. Terminal values deliberately have no outgoing edges. */
public enum MissionStatus {
    CREATED, PENDING, DISPATCHED, RUNNING, PAUSED, SUCCESS, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
