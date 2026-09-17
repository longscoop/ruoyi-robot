package com.robot.platform.mqtt;

@FunctionalInterface
public interface RobotMessageHandler<T> {
    void handle(RobotMessageEnvelope<T> envelope);
}
