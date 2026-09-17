package com.robot.platform.member.binding.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.binding.service.MemberRobotBindCommand;
import com.robot.platform.member.binding.service.MemberRobotBindingService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Binding changes are constrained to the administrator's tenant context. */
@RestController @RequestMapping("/member/robot-bindings") @Validated @RequiredArgsConstructor
public class MemberRobotBindingAdminController {
    private final MemberRobotBindingService bindings;
    @PostMapping @PreAuthorize("@ss.hasPermission('member:binding:create')")
    public CommonResult<Long> bind(@Valid @RequestBody BindReqVO request) {
        return success(bindings.bind(new MemberRobotBindCommand(TenantContextHolder.getRequiredTenantId(), request.getMemberId(),
                request.getRobotId(), request.getRole(), request.getStatus())));
    }
    @GetMapping @PreAuthorize("@ss.hasPermission('member:binding:query')")
    public CommonResult<List<BindingRespVO>> list() {
        return success(bindings.list(TenantContextHolder.getRequiredTenantId()).stream()
                .map(binding -> new BindingRespVO(binding.getId(), binding.getMemberId(), binding.getRobotId(), binding.getRole(), binding.getStatus())).toList());
    }
    @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('member:binding:delete')")
    public CommonResult<Boolean> remove(@PathVariable long id) {
        bindings.remove(TenantContextHolder.getRequiredTenantId(), id); return success(true);
    }
    @Data public static class BindReqVO { private Long memberId; private Long robotId; private String role = "READ"; private String status = "ENABLED"; }
    public record BindingRespVO(long id, long memberId, long robotId, String role, String status) { }
}
