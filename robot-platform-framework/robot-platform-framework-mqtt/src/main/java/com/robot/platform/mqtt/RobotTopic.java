package com.robot.platform.mqtt;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical per-device topic. It is exactly {@code robot/tenant/product/device/channel}; wildcards,
 * escapes and surplus segments are rejected so authorization can use full-string equality.
 */
public record RobotTopic(String tenantNamespace, String productKey, String deviceSn, Channel channel) {
    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    public enum Channel { COMMAND, OTA, STATE, EVENT }

    public RobotTopic {
        requireComponent(tenantNamespace); requireComponent(productKey); requireComponent(deviceSn);
        if (channel == null) throw new RobotProtocolException("topic channel is required");
    }
    public static RobotTopic of(String tenantNamespace, String productKey, String deviceSn, Channel channel) {
        return new RobotTopic(tenantNamespace, productKey, deviceSn, channel);
    }
    public static RobotTopic parse(String value) {
        if (value == null || value.indexOf('+') >= 0 || value.indexOf('#') >= 0) throw new RobotProtocolException("invalid topic");
        String[] parts = value.split("/", -1);
        if (parts.length != 5 || !"robot".equals(parts[0])) throw new RobotProtocolException("invalid topic");
        try { return new RobotTopic(parts[1], parts[2], parts[3], Channel.valueOf(parts[4].toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { throw new RobotProtocolException("invalid topic", e); }
    }
    public String value() { return "robot/" + tenantNamespace + "/" + productKey + "/" + deviceSn + "/" + channel.name().toLowerCase(Locale.ROOT); }
    private static void requireComponent(String component) {
        if (component == null || !COMPONENT.matcher(component).matches()) throw new RobotProtocolException("invalid topic component");
    }
}
