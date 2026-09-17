package com.robot.platform.robot.status.controller.device;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.robot.status.controller.device.vo.DeviceRobotConfigRespVO;
import com.robot.platform.robot.status.service.DeviceHeartbeatApplicationService;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

/** Device endpoints derive device/tenant/robot only from the authenticated session and database binding. */
@RestController @RequestMapping("/device-api/device") @RequiredArgsConstructor
public class DeviceHeartbeatController {
    private final DeviceHeartbeatApplicationService application;
    @PostMapping("/heartbeat") public CommonResult<RobotHeartbeatService.AcceptResult> heartbeat(@RequestBody byte[] rawBody) {
        RobotAuthenticatedPrincipal principal = principal();
        return success(application.accept(principal.getTenantId(), principal.getId(), rawBody));
    }
    @GetMapping("/config") public CommonResult<DeviceRobotConfigRespVO> config() {
        RobotAuthenticatedPrincipal principal = principal();
        return success(application.config(principal.getTenantId(), principal.getId()));
    }
    private RobotAuthenticatedPrincipal principal() {
        if (!(SecurityFrameworkUtils.getLoginUser() instanceof RobotAuthenticatedPrincipal principal)
                || principal.getAudience() != ApiAudience.DEVICE) throw new org.springframework.security.access.AccessDeniedException("DEVICE principal required");
        return principal;
    }
}
