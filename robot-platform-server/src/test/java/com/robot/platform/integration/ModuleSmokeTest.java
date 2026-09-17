package com.robot.platform.integration;

import com.robot.platform.device.DeviceModuleConfiguration;
import com.robot.platform.member.MemberModuleConfiguration;
import com.robot.platform.robot.RobotModuleConfiguration;
import com.robot.platform.tenant.TenantModuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleSmokeTest {

    @Test
    void exposesEveryCoreModuleConfiguration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan("com.robot.platform.tenant", "com.robot.platform.member",
                    "com.robot.platform.device", "com.robot.platform.robot");
            context.refresh();

            assertThat(context.getBeansOfType(TenantModuleConfiguration.class)).hasSize(1);
            assertThat(context.getBeansOfType(MemberModuleConfiguration.class)).hasSize(1);
            assertThat(context.getBeansOfType(DeviceModuleConfiguration.class)).hasSize(1);
            assertThat(context.getBeansOfType(RobotModuleConfiguration.class)).hasSize(1);
        }
    }
}
