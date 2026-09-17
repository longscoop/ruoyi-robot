package com.robot.platform.device.device.service;

/** Typed outbox/event port for future session and audit listeners; implementations must not contain secrets. */
public interface DeviceCredentialRevocationPublisher {
    void publish(DeviceCredentialsRevokedEvent event);
}
