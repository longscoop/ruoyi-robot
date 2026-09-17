package com.robot.platform.device.device.enums;

public enum DeviceLifecycle {
    UNACTIVATED, ACTIVATED, DISABLED, MAINTENANCE, SCRAPPED;
    public static DeviceLifecycle of(String status) {
        try { return valueOf(status); } catch (RuntimeException e) { throw new IllegalArgumentException("invalid device lifecycle", e); }
    }
}
