package com.robot.platform.device.device;

import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.DeviceLifecyclePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceLifecycleTest {

    @ParameterizedTest
    @CsvSource({
            "UNACTIVATED,ACTIVATED", "ACTIVATED,DISABLED", "ACTIVATED,MAINTENANCE",
            "MAINTENANCE,ACTIVATED", "DISABLED,ACTIVATED", "DISABLED,SCRAPPED"
    })
    void allowsDeclaredLifecycleTransitions(DeviceLifecycle from, DeviceLifecycle to) {
        assertThat(DeviceLifecyclePolicy.canTransition(from, to)).isTrue();
    }

    @Test
    void rejectsScrappedToActivated() {
        assertThat(DeviceLifecyclePolicy.canTransition(DeviceLifecycle.SCRAPPED, DeviceLifecycle.ACTIVATED)).isFalse();
    }

    @Test
    void rejectsUnactivatedToScrappedBecauseInventoryMustRemainTenantless() {
        assertThat(DeviceLifecyclePolicy.canTransition(DeviceLifecycle.UNACTIVATED, DeviceLifecycle.SCRAPPED)).isFalse();
    }
}
