package com.robot.platform.mqtt;

/** Extensible schema hook; implementations reject invalid DTO values with RobotProtocolException. */
@FunctionalInterface public interface RobotPayloadValidator<T> { void validate(T payload); }
