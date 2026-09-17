package com.robot.platform.device.mqtt.controller;

import cn.hutool.crypto.SecureUtil;
import com.robot.platform.framework.ratelimiter.core.redis.RateLimiterRedisDAO;
import com.robot.platform.mqtt.RobotMqttProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Redis-backed post-token limiter; cloud and device principals intentionally have separate rates. */
@Component
public class RedisMqttCallbackRateLimiter implements MqttCallbackRateLimiter {
    private final RateLimiterRedisDAO rateLimiter;
    private final RobotMqttProperties properties;

    public RedisMqttCallbackRateLimiter(RateLimiterRedisDAO rateLimiter, RobotMqttProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        properties.requireCallbackRateLimitConfiguration();
    }

    @Override
    public boolean tryAcquire(String endpoint, String mqttUsername, PrincipalType principalType) {
        try {
            int limit = principalType == PrincipalType.CLOUD
                    ? properties.getCallbackRateLimit().getCloudPerMinute()
                    : properties.getCallbackRateLimit().getDevicePerMinute();
            return Boolean.TRUE.equals(rateLimiter.tryAcquire(key(endpoint, mqttUsername), limit, 1, TimeUnit.MINUTES));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static String key(String endpoint, String mqttUsername) {
        return SecureUtil.sha256("mqtt-callback:principal:" + safe(endpoint) + ':' + safe(mqttUsername));
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
