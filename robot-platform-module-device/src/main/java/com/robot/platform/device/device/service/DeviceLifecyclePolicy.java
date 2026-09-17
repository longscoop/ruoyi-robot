package com.robot.platform.device.device.service;

import com.robot.platform.device.device.enums.DeviceLifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Central, immutable lifecycle graph; controllers must never duplicate this policy. */
public final class DeviceLifecyclePolicy {
    private static final Map<DeviceLifecycle, EnumSet<DeviceLifecycle>> TRANSITIONS;
    static {
        Map<DeviceLifecycle, EnumSet<DeviceLifecycle>> transitions = new EnumMap<>(DeviceLifecycle.class);
        transitions.put(DeviceLifecycle.UNACTIVATED, EnumSet.of(DeviceLifecycle.ACTIVATED));
        transitions.put(DeviceLifecycle.ACTIVATED, EnumSet.of(DeviceLifecycle.DISABLED, DeviceLifecycle.MAINTENANCE));
        transitions.put(DeviceLifecycle.MAINTENANCE, EnumSet.of(DeviceLifecycle.ACTIVATED, DeviceLifecycle.DISABLED, DeviceLifecycle.SCRAPPED));
        transitions.put(DeviceLifecycle.DISABLED, EnumSet.of(DeviceLifecycle.ACTIVATED, DeviceLifecycle.MAINTENANCE, DeviceLifecycle.SCRAPPED));
        transitions.put(DeviceLifecycle.SCRAPPED, EnumSet.noneOf(DeviceLifecycle.class));
        TRANSITIONS = Map.copyOf(transitions);
    }
    private DeviceLifecyclePolicy() { }
    public static boolean canTransition(DeviceLifecycle from, DeviceLifecycle to) {
        return from != null && to != null && TRANSITIONS.get(from).contains(to);
    }
}
