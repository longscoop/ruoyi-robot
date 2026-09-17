package com.robot.platform.device.mqtt.controller;

/** Coarse, pre-authentication protection keyed only by remote address. */
@FunctionalInterface
public interface MqttCallbackPreAuthRateLimiter {
    boolean tryAcquire(String clientIp);
}
