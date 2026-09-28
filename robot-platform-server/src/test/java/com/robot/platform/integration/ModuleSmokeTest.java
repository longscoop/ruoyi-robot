package com.robot.platform.integration;

import com.robot.platform.device.DeviceModuleConfiguration;
import com.robot.platform.member.MemberModuleConfiguration;
import com.robot.platform.robot.RobotModuleConfiguration;
import com.robot.platform.tenant.TenantModuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleSmokeTest {

    @Test
    void exposesEveryCoreModuleConfiguration() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));

        assertThat(scanner.findCandidateComponents("com.robot.platform.tenant"))
                .extracting(bean -> bean.getBeanClassName())
                .contains(TenantModuleConfiguration.class.getName());
        assertThat(scanner.findCandidateComponents("com.robot.platform.member"))
                .extracting(bean -> bean.getBeanClassName())
                .contains(MemberModuleConfiguration.class.getName());
        assertThat(scanner.findCandidateComponents("com.robot.platform.device"))
                .extracting(bean -> bean.getBeanClassName())
                .contains(DeviceModuleConfiguration.class.getName());
        assertThat(scanner.findCandidateComponents("com.robot.platform.robot"))
                .extracting(bean -> bean.getBeanClassName())
                .contains(RobotModuleConfiguration.class.getName());
    }
}
