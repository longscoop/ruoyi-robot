package com.robot.platform.device.mqtt.service;

import com.robot.platform.device.device.service.DeviceCredentialsRevokedEvent;

/**
 * Boundary for invalidating broker sessions after a device credential rotation. A production
 * EMQX management-API adapter can implement this without coupling device domain code to EMQX.
 */
public interface DeviceMqttSessionRevocationPort {
    void revoke(DeviceCredentialsRevokedEvent event);
}
