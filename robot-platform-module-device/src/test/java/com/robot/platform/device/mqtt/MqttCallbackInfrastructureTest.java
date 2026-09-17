package com.robot.platform.device.mqtt;

import com.robot.platform.framework.ratelimiter.core.redis.RateLimiterRedisDAO;
import com.robot.platform.device.device.service.DeviceCredentialsRevokedEvent;
import com.robot.platform.device.mqtt.controller.MqttCallbackRateLimiter;
import com.robot.platform.device.mqtt.controller.RedisMqttCallbackPreAuthRateLimiter;
import com.robot.platform.device.mqtt.controller.RedisMqttCallbackRateLimiter;
import com.robot.platform.device.mqtt.service.DeviceMqttCredentialRevocationListener;
import com.robot.platform.device.mqtt.service.DeviceMqttSessionRevocationPort;
import com.robot.platform.mqtt.RobotMqttProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MqttCallbackInfrastructureTest {
    @Test
    void postTokenLimiterHashesPrincipalAndGivesCloudASeparateHigherConfiguredCapacity() {
        RateLimiterRedisDAO redis = mock(RateLimiterRedisDAO.class);
        when(redis.tryAcquire(anyString(), anyInt(), anyInt(), any())).thenReturn(true);
        RobotMqttProperties properties = rateProperties();
        RedisMqttCallbackRateLimiter limiter = new RedisMqttCallbackRateLimiter(redis, properties);

        assertThat(limiter.tryAcquire("authorize", "tenant/p/sn", MqttCallbackRateLimiter.PrincipalType.DEVICE)).isTrue();
        verify(redis).tryAcquire(argThat(key -> !key.contains("tenant/p/sn") && !key.contains("password") && !key.contains("token")),
                eq(7), eq(1), any());
        assertThat(limiter.tryAcquire("authorize", "cloud", MqttCallbackRateLimiter.PrincipalType.CLOUD)).isTrue();
        verify(redis).tryAcquire(anyString(), eq(7000), eq(1), any());
    }

    @Test
    void preAuthLimiterUsesIndependentIpOnlyCapacityAndBothLayersFailClosedOnRedisErrors() {
        RateLimiterRedisDAO redis = mock(RateLimiterRedisDAO.class);
        when(redis.tryAcquire(anyString(), anyInt(), anyInt(), any())).thenReturn(true);
        RobotMqttProperties properties = rateProperties();
        RedisMqttCallbackPreAuthRateLimiter preAuth = new RedisMqttCallbackPreAuthRateLimiter(redis, properties);
        RedisMqttCallbackRateLimiter principal = new RedisMqttCallbackRateLimiter(redis, properties);

        assertThat(preAuth.tryAcquire("192.0.2.10")).isTrue();
        verify(redis).tryAcquire(argThat(key -> !key.contains("192.0.2.10") && !key.contains("token")), eq(31), eq(1), any());
        when(redis.tryAcquire(anyString(), anyInt(), anyInt(), any())).thenThrow(new IllegalStateException("redis unavailable"));
        assertThat(preAuth.tryAcquire("192.0.2.10")).isFalse();
        assertThat(principal.tryAcquire("authenticate", "tenant/p/sn", MqttCallbackRateLimiter.PrincipalType.DEVICE)).isFalse();
    }

    @Test
    void rateLimitConfigurationRejectsNonPositiveCapacity() {
        RobotMqttProperties properties = rateProperties();
        properties.getCallbackRateLimit().setCloudPerMinute(0);

        assertThatThrownBy(() -> new RedisMqttCallbackRateLimiter(mock(RateLimiterRedisDAO.class), properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void forwardsCredentialRotationToAnyBrokerSessionRevocationAdapter() {
        DeviceMqttSessionRevocationPort port = mock(DeviceMqttSessionRevocationPort.class);
        DeviceMqttCredentialRevocationListener listener = new DeviceMqttCredentialRevocationListener(List.of(port));
        DeviceCredentialsRevokedEvent event = new DeviceCredentialsRevokedEvent(7, 10, 5, "rotated");

        listener.revoke(event);

        verify(port).revoke(event);
    }

    private static RobotMqttProperties rateProperties() {
        RobotMqttProperties properties = new RobotMqttProperties();
        properties.getCallbackRateLimit().setPreAuthPerMinute(31);
        properties.getCallbackRateLimit().setDevicePerMinute(7);
        properties.getCallbackRateLimit().setCloudPerMinute(7000);
        return properties;
    }
}
