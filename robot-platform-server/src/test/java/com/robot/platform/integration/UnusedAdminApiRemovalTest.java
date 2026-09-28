package com.robot.platform.integration;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UnusedAdminApiRemovalTest {

    @Test
    void disabledModuleFallbackAndPublicDebugControllersAreNotRegistered() {
        assertThat(restControllers("com.robot.platform.server.controller")).isEmpty();
    }

    @Test
    void infrastructureCrudExamplesAreNotRegistered() {
        assertThat(restControllers("com.robot.platform.module.infra.controller.admin.demo")).isEmpty();
    }

    @Test
    void infrastructureExampleMenuIsHidden() throws Exception {
        String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));
        assertThat(sql).contains("WHERE `id` = 83 OR `parent_id` = 83");
    }

    private static java.util.List<String> restControllers(String packageName) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents(packageName).stream()
                .map(bean -> bean.getBeanClassName())
                .toList();
    }
}
