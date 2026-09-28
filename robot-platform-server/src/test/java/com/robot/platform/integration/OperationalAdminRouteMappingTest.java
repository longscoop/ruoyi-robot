package com.robot.platform.integration;

import com.robot.platform.ai.digitalhuman.controller.admin.AiDigitalHumanAdminController;
import com.robot.platform.ai.conversation.controller.admin.AiConversationAdminController;
import com.robot.platform.ai.memory.controller.admin.AiMemoryAdminController;
import com.robot.platform.ai.realtime.controller.admin.AiRealtimeSessionAdminController;
import com.robot.platform.device.device.controller.admin.DeviceController;
import com.robot.platform.device.group.controller.admin.DeviceGroupController;
import com.robot.platform.device.product.controller.admin.ProductController;
import com.robot.platform.robot.dashboard.controller.admin.RobotDashboardController;
import com.robot.platform.robot.mission.controller.admin.MissionController;
import com.robot.platform.robot.robot.controller.admin.RobotController;
import com.robot.platform.tenant.quota.controller.admin.TenantQuotaController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalAdminRouteMappingTest {

    @Test
    void robotPlatformControllersUsePathsRelativeToTheAdminApiPrefix() {
        assertRoot(ProductController.class, "/device/products");
        assertRoot(DeviceController.class, "/device/devices");
        assertRoot(DeviceGroupController.class, "/device/groups");
        assertRoot(RobotController.class, "/robot/robots");
        assertRoot(MissionController.class, "/robot/missions");
        assertRoot(TenantQuotaController.class, "/tenant/quotas");
        assertRoot(RobotDashboardController.class, "/robot/dashboard");
        assertRoot(AiRealtimeSessionAdminController.class, "/ai/realtime-sessions");
        assertRoot(AiDigitalHumanAdminController.class, "/ai/digital-humans");
        assertRoot(AiMemoryAdminController.class, "/ai/memories");
        assertRoot(AiConversationAdminController.class, "/ai/conversations");
    }

    @Test
    void unsupportedLegacyMemberMenuIsHidden() throws Exception {
        String sql = Files.readString(Path.of("../sql/mysql/robot-platform.sql"));
        assertTrue(sql.contains("WHERE `id` IN (373,"));
    }

    private static void assertRoot(Class<?> controller, String path) {
        assertArrayEquals(new String[]{path}, controller.getAnnotation(RequestMapping.class).value());
    }
}
