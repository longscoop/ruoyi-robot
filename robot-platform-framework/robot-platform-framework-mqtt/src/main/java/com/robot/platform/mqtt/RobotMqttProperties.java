package com.robot.platform.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker and callback credentials are intentionally separate: the callback token authenticates
 * EMQX HTTP callbacks, while the cloud credentials authenticate the server MQTT client.
 */
@ConfigurationProperties(prefix = "robot.mqtt")
public class RobotMqttProperties {
    private boolean enabled;
    private String host;
    private int port = 1883;
    private int maxPayloadBytes = 65_536;
    private String callbackToken;
    private final Cloud cloud = new Cloud();
    private final CallbackRateLimit callbackRateLimit = new CallbackRateLimit();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }
    public Cloud getCloud() { return cloud; }
    public CallbackRateLimit getCallbackRateLimit() { return callbackRateLimit; }

    public void requireEnabledBrokerConfiguration() {
        if (blank(host) || port < 1 || port > 65_535 || maxPayloadBytes < 1 || blank(cloud.clientId)
                || blank(cloud.username) || blank(cloud.password)) {
            throw new IllegalStateException("enabled MQTT requires host, port and cloud broker credentials");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public void requireCallbackRateLimitConfiguration() {
        if (callbackRateLimit.preAuthPerMinute < 1 || callbackRateLimit.devicePerMinute < 1
                || callbackRateLimit.cloudPerMinute < 1) {
            throw new IllegalStateException("MQTT callback rate limits must be positive");
        }
    }

    public static class Cloud {
        private String clientId;
        private String username;
        private String password;
        private int credentialVersion = 1;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getCredentialVersion() { return credentialVersion; }
        public void setCredentialVersion(int credentialVersion) { this.credentialVersion = credentialVersion; }
    }

    /** The coarse broker/IP budget accommodates aggregate traffic; principal budgets follow token validation. */
    public static class CallbackRateLimit {
        private int preAuthPerMinute = 60_000;
        private int devicePerMinute = 120;
        private int cloudPerMinute = 6_000;

        public int getPreAuthPerMinute() { return preAuthPerMinute; }
        public void setPreAuthPerMinute(int preAuthPerMinute) { this.preAuthPerMinute = preAuthPerMinute; }
        public int getDevicePerMinute() { return devicePerMinute; }
        public void setDevicePerMinute(int devicePerMinute) { this.devicePerMinute = devicePerMinute; }
        public int getCloudPerMinute() { return cloudPerMinute; }
        public void setCloudPerMinute(int cloudPerMinute) { this.cloudPerMinute = cloudPerMinute; }
    }
}
