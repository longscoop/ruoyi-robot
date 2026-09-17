package com.robot.platform.mqtt;

import java.util.Locale;

/** EMQX operation direction; publishing and subscribing are never interchangeable. */
public enum MqttAuthorizationAction {
    PUBLISH, SUBSCRIBE;
    public static MqttAuthorizationAction parse(String action) {
        if (action == null) throw new RobotProtocolException("missing mqtt action");
        try { return valueOf(action.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new RobotProtocolException("invalid mqtt action", e); }
    }
}
