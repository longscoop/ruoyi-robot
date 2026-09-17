package com.robot.platform.device.mqtt.service;

/** Server-side MQTT identity projection. It is assembled from persistence and must never be logged. */
public record DeviceMqttIdentity(long deviceId, long tenantId, long robotId, String tenantNamespace, String productKey,
                                 String deviceSn, String mqttUsername, String mqttSecretHash, int credentialVersion) { }
