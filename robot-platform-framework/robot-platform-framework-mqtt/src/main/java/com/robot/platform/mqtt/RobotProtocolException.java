package com.robot.platform.mqtt;

/** Thrown when untrusted MQTT protocol input violates a protocol invariant. */
public class RobotProtocolException extends RuntimeException {
    public RobotProtocolException(String message) { super(message); }
    public RobotProtocolException(String message, Throwable cause) { super(message, cause); }
}
