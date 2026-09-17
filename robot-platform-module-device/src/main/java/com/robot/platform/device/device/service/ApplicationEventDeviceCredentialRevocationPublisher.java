package com.robot.platform.device.device.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes secret-free revocation events; listeners are optional, publication is not. */
@Component
@RequiredArgsConstructor
public class ApplicationEventDeviceCredentialRevocationPublisher implements DeviceCredentialRevocationPublisher {
    private final ApplicationEventPublisher publisher;
    @Override public void publish(DeviceCredentialsRevokedEvent event) { publisher.publishEvent(event); }
}
