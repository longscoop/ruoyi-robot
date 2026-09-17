package com.robot.platform.robot.status.service;

/** Signals that a heartbeat recreated a missing Redis projection while the offline DB transition was in flight. */
public final class ConcurrentHeartbeatProjectionException extends RuntimeException {
    public ConcurrentHeartbeatProjectionException() { super("concurrent heartbeat won missing projection restore"); }
}
