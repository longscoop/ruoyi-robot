package com.robot.platform.robot.mission.controller.device;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.mission.service.DeviceMissionApplicationService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

/** DEVICE-only fallback; mission identity in the path is cross-checked against the signed envelope. */
@RestController
@RequestMapping("/device-api/missions")
@RequiredArgsConstructor
public class DeviceMissionController {
    private final DeviceMissionApplicationService application;
    @PostMapping("/{missionId}/ack")
    public CommonResult<MessageHandleResult> acknowledge(@PathVariable long missionId, @RequestBody byte[] raw) {
        RobotAuthenticatedPrincipal principal = principal();
        return success(application.acknowledge(principal.getTenantId(), principal.getId(), missionId, raw));
    }
    @PostMapping("/{missionId}/events")
    public CommonResult<MessageHandleResult> event(@PathVariable long missionId, @RequestBody byte[] raw) {
        RobotAuthenticatedPrincipal principal = principal();
        return success(application.event(principal.getTenantId(), principal.getId(), missionId, raw));
    }
    private static RobotAuthenticatedPrincipal principal() {
        if (!(SecurityFrameworkUtils.getLoginUser() instanceof RobotAuthenticatedPrincipal principal)
                || principal.getAudience() != ApiAudience.DEVICE) throw new org.springframework.security.access.AccessDeniedException("DEVICE principal required");
        return principal;
    }
}
