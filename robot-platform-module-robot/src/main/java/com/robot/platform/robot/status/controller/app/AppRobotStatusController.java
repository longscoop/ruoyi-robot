package com.robot.platform.robot.status.controller.app;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.member.binding.service.MemberRobotAccessService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

/** Member access is checked before even reading the Redis projection. */
@RestController @RequestMapping("/app-api/robots") @RequiredArgsConstructor
public class AppRobotStatusController {
    private final MemberRobotAccessService access;
    private final RobotLiveStatusQueryService statuses;
    @GetMapping("/{id}/status") public CommonResult<RobotLiveStatus> status(@PathVariable long id) {
        if (!(SecurityFrameworkUtils.getLoginUser() instanceof RobotAuthenticatedPrincipal principal)
                || principal.getAudience() != ApiAudience.APP) throw new org.springframework.security.access.AccessDeniedException("APP principal required");
        access.requireReadable(principal.getTenantId(), principal.getId(), id);
        return success(statuses.find(principal.getTenantId(), id).orElse(null));
    }
}
