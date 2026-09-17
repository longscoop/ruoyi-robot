package com.robot.platform.robot.dashboard.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.robot.platform.robot.dashboard.model.RobotDashboard;
import com.robot.platform.robot.dashboard.service.RobotDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId;

@Tag(name = "管理后台 - 机器人仪表盘")
@RestController
// The web framework prepends /admin-api for every controller in this package.
@RequestMapping("/robot/dashboard")
@RequiredArgsConstructor
public class RobotDashboardController {
    private final RobotDashboardService dashboard;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('robot:robot:query')")
    public CommonResult<RobotDashboard> get() {
        return success(dashboard.getDashboard(getRequiredTenantId()));
    }
}
