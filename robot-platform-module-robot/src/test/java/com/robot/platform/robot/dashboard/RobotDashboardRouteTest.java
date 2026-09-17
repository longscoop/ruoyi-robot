package com.robot.platform.robot.dashboard;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration;
import com.robot.platform.robot.dashboard.controller.admin.RobotDashboardController;
import com.robot.platform.robot.dashboard.service.RobotDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Ensures the framework's package-driven admin prefix is applied exactly once. */
class RobotDashboardRouteTest {
    @Test
    void exposesSingleAdminPrefixRatherThanDoublePrefix() {
        WebProperties properties = new WebProperties();
        RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping) new YudaoWebAutoConfiguration()
                .webMvcRegistrations(properties).getRequestMappingHandlerMapping();
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("robotDashboardController",
                new RobotDashboardController(mock(RobotDashboardService.class)));
        context.refresh();
        mapping.setApplicationContext(context);
        mapping.afterPropertiesSet();

        Set<String> paths = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()).collect(java.util.stream.Collectors.toSet());

        assertThat(paths).contains("/admin-api/robot/dashboard");
        assertThat(paths).doesNotContain("/admin-api/admin-api/robot/dashboard");
    }
}
