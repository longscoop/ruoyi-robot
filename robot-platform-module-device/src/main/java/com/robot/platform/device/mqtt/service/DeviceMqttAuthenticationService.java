package com.robot.platform.device.mqtt.service;

import java.util.Optional;

/** Resolves an active, bound device from its exact persisted MQTT username on every EMQX callback. */
public interface DeviceMqttAuthenticationService {
    Optional<DeviceMqttIdentity> findActiveByUsername(String mqttUsername);
}
