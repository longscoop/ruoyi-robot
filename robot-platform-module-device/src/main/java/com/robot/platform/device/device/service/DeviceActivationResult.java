package com.robot.platform.device.device.service;

/** One-time result; do not persist or log this value. */
public record DeviceActivationResult(long deviceId, long robotId, String mqttUsername, String mqttSecret,
                                     String httpSecret, int credentialVersion) {
    @Override public String toString() {
        return "DeviceActivationResult[deviceId=" + deviceId + ", robotId=" + robotId + ", mqttUsername="
                + mqttUsername + ", mqttSecret=<redacted>, httpSecret=<redacted>, credentialVersion="
                + credentialVersion + "]";
    }
}
