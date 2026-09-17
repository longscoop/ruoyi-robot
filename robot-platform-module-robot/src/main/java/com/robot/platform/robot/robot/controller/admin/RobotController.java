package com.robot.platform.robot.robot.controller.admin;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.common.pojo.PageResult;
import com.robot.platform.robot.robot.controller.admin.vo.RobotCapabilityRespVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotCapabilityUpsertReqVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotPageReqVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotRespVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotUpdateReqVO;
import com.robot.platform.robot.robot.convert.RobotConvert;
import com.robot.platform.robot.robot.service.RobotCapabilityService;
import com.robot.platform.robot.robot.service.RobotService;
import com.robot.platform.robot.robot.service.dto.RobotRespDTO;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

/** Robot creation/deletion is intentionally absent: DeviceService owns that lifecycle. */
@Tag(name = "管理后台 - 机器人")
@RestController
@RequestMapping("/admin-api/robot/robots")
@Validated
@RequiredArgsConstructor
public class RobotController {
    private final RobotService robotService;
    private final RobotCapabilityService capabilityService;
    private final RobotLiveStatusQueryService liveStatuses;

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('robot:robot:query')")
    public CommonResult<RobotRespVO> get(@PathVariable long id) {
        return success(RobotConvert.INSTANCE.convert(robotService.get(id)));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('robot:robot:query')")
    public CommonResult<RobotLiveStatus> status(@PathVariable long id) {
        robotService.get(id); // tenant interceptor/RBAC has already constrained the row.
        return success(liveStatuses.find(com.robot.platform.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(), id).orElse(null));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('robot:robot:query')")
    public CommonResult<PageResult<RobotRespVO>> page(@Valid RobotPageReqVO query) {
        PageResult<RobotRespDTO> page = robotService.page(RobotConvert.INSTANCE.convert(query));
        return success(new PageResult<>(page.getList().stream().map(RobotConvert.INSTANCE::convert).toList(), page.getTotal()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新机器人名称")
    @PreAuthorize("@ss.hasPermission('robot:robot:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody RobotUpdateReqVO request) {
        robotService.update(id, RobotConvert.INSTANCE.convert(request));
        return success(true);
    }

    @GetMapping("/{id}/capabilities")
    @PreAuthorize("@ss.hasPermission('robot:robot:query')")
    public CommonResult<List<RobotCapabilityRespVO>> capabilities(@PathVariable long id) {
        return success(RobotConvert.INSTANCE.convertCapabilities(capabilityService.list(id)));
    }

    @PutMapping("/{id}/capabilities")
    @PreAuthorize("@ss.hasPermission('robot:robot:update')")
    public CommonResult<Boolean> upsertCapability(@PathVariable long id,
                                                   @RequestParam @NotBlank @Size(max = 64) String capabilityCode,
                                                   @Valid @RequestBody RobotCapabilityUpsertReqVO request) {
        capabilityService.upsert(id, capabilityCode, request.getConfiguration());
        return success(true);
    }
}
