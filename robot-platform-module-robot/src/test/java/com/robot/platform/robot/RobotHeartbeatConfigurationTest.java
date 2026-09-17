package com.robot.platform.robot;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RobotHeartbeatConfigurationTest {
    @Test void snapshotIntervalMustRemainShorterThanOfflineTimeout() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new RobotModuleConfiguration()
                .robotHeartbeatSnapshotInterval(Duration.ofMinutes(10), Duration.ofMinutes(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shorter than offline timeout");
    }

    @Test void validSnapshotAndOfflineIntervalsStartSuccessfully() {
        assertThat(new RobotModuleConfiguration().robotHeartbeatSnapshotInterval(Duration.ofSeconds(30), Duration.ofMinutes(2)))
                .isEqualTo(Duration.ofSeconds(30));
    }
}
