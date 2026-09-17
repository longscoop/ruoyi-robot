package com.robot.platform.device.mqtt.controller;

import cn.hutool.crypto.SecureUtil;
import com.robot.platform.framework.ratelimiter.core.redis.RateLimiterRedisDAO;
import com.robot.platform.mqtt.RobotMqttProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Redis-backed pre-auth limiter. It sees no callback token, MQTT body, topic or password. */
@Component
public class RedisMqttCallbackPreAuthRateLimiter implements MqttCallbackPreAuthRateLimiter {
    private final RateLimiterRedisDAO rateLimiter;
    private final RobotMqttProperties properties;

    public RedisMqttCallbackPreAuthRateLimiter(RateLimiterRedisDAO rateLimiter, RobotMqttProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        properties.requireCallbackRateLimitConfiguration();
    }

    @Override
    public boolean tryAcquire(String clientIp) {
        try {
            return Boolean.TRUE.equals(rateLimiter.tryAcquire(key(clientIp),
                    properties.getCallbackRateLimit().getPreAuthPerMinute(), 1, TimeUnit.MINUTES));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static String key(String clientIp) { return SecureUtil.sha256("mqtt-callback:pre-auth:" + safe(clientIp)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
