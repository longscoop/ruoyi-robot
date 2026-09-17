package com.robot.platform.device.auth;

import org.springframework.security.core.AuthenticationException;

/** Deliberately uniform device-authentication failure; its message never contains sensitive input. */
public final class DeviceTokenAuthenticationException extends AuthenticationException {
    public DeviceTokenAuthenticationException() { super("device authentication failed"); }
}
