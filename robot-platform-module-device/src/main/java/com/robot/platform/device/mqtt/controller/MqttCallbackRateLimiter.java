package com.robot.platform.device.mqtt.controller;

/** Post-callback-token limiter keyed by endpoint and MQTT principal username, never request secrets. */
public interface MqttCallbackRateLimiter {
    boolean tryAcquire(String endpoint, String mqttUsername, PrincipalType principalType);

    enum PrincipalType { CLOUD, DEVICE }
}
