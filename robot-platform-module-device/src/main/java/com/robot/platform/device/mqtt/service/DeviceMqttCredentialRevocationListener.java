package com.robot.platform.device.mqtt.service;

import com.robot.platform.device.device.service.DeviceCredentialsRevokedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Fans credential-revocation events out to optional broker adapters; AuthZ version checks stay fail-closed. */
@Component
@RequiredArgsConstructor
public class DeviceMqttCredentialRevocationListener {
    private final List<DeviceMqttSessionRevocationPort> revocationPorts;

    @EventListener
    public void revoke(DeviceCredentialsRevokedEvent event) {
        revocationPorts.forEach(port -> port.revoke(event));
    }
}
