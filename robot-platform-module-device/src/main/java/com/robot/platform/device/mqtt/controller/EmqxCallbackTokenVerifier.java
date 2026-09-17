package com.robot.platform.device.mqtt.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Fail-closed, constant-time verifier for the service credential shared only with EMQX. */
final class EmqxCallbackTokenVerifier {
    private final byte[] expected;
    EmqxCallbackTokenVerifier(String configuredToken) {
        this.expected = configuredToken == null || configuredToken.isBlank() ? null : configuredToken.getBytes(StandardCharsets.UTF_8);
    }
    boolean matches(String candidate) {
        if (expected == null || candidate == null) return false;
        return MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8));
    }
}
