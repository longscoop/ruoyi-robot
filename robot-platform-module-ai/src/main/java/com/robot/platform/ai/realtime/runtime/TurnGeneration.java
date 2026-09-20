package com.robot.platform.ai.realtime.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TurnGeneration {

    private final String turnId;
    private final long generation;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public TurnGeneration(String turnId, long generation) {
        this.turnId = requireText(turnId, "turnId");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        this.generation = generation;
    }

    public String turnId() {
        return turnId;
    }

    public long generation() {
        return generation;
    }

    public boolean cancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean matches(String turnId, long generation) {
        return !cancelled()
                && this.generation == generation
                && this.turnId.equals(turnId);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "TurnGeneration[turnId=" + turnId
                + ", generation=" + generation
                + ", cancelled=" + cancelled() + "]";
    }
}
