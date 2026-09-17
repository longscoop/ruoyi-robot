package com.robot.platform.robot;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
public class RobotModuleConfiguration {
    @Bean Clock robotHeartbeatClock() { return Clock.systemUTC(); }
    @Bean Duration robotHeartbeatStatusTtl(@Value("${robot.heartbeat.status-ttl:PT10M}") Duration value) { return value; }
    @Bean Duration robotHeartbeatSnapshotInterval(
            @Value("${robot.heartbeat.snapshot-interval:PT1M}") Duration value,
            @Value("${robot.heartbeat.offline-timeout:PT2M}") Duration offlineTimeout) {
        if (value == null || value.isNegative() || offlineTimeout == null || offlineTimeout.isZero()
                || offlineTimeout.isNegative() || value.compareTo(offlineTimeout) >= 0) {
            throw new IllegalArgumentException("heartbeat snapshot interval must be shorter than offline timeout");
        }
        return value;
    }
}
