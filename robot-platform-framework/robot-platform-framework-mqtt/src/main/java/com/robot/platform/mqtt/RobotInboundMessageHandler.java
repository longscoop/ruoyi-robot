package com.robot.platform.mqtt;

@FunctionalInterface public interface RobotInboundMessageHandler<T> { void handle(RobotInboundMessage<T> message); }
