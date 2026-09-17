package com.robot.platform.robot.status.model;

import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Strict v1 telemetry. The robot id is only an integrity assertion against the authenticated device. */
public record HeartbeatPayload(long robotId, int battery, int cpuUsage, int memoryUsage, int temperatureCelsius,
                               String workStatus, String ipAddress, String currentMissionId, String softwareVersion) {
    public HeartbeatPayload {
        if (robotId <= 0 || battery < 0 || battery > 100 || cpuUsage < 0 || cpuUsage > 100
                || memoryUsage < 0 || memoryUsage > 100 || temperatureCelsius < -80 || temperatureCelsius > 180
                || workStatus == null || ipAddress == null || ipAddress.length() > 45
                || (currentMissionId != null && currentMissionId.length() > 128)
                || (softwareVersion != null && softwareVersion.length() > 64)) throw new IllegalArgumentException("invalid heartbeat payload");
        validateIpLiteral(ipAddress);
        try { RobotWorkStatus.valueOf(workStatus); } catch (Exception e) { throw new IllegalArgumentException("invalid work status", e); }
    }

    private static void validateIpLiteral(String value) {
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("invalid ip address");
            try {
                if (!(InetAddress.getByName(value) instanceof Inet6Address)) {
                    throw new IllegalArgumentException("invalid ip address");
                }
                return;
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException("invalid ip address", exception);
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) throw new IllegalArgumentException("invalid ip address");
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("invalid ip address");
            }
            if (Integer.parseInt(octet) > 255) throw new IllegalArgumentException("invalid ip address");
        }
    }
}
