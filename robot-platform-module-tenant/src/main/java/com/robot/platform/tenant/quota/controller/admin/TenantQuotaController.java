package com.robot.platform.tenant.quota.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.tenant.quota.controller.admin.vo.TenantQuotaRespVO;
import com.robot.platform.tenant.quota.controller.admin.vo.TenantQuotaUpdateReqVO;
import com.robot.platform.tenant.quota.dal.dataobject.TenantUsageDO;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 租户机器人配额")
@RestController
@RequestMapping("/admin-api/tenant/quotas")
@Validated
@RequiredArgsConstructor
public class TenantQuotaController {
    private final TenantRobotQuotaService quotaService;
    private final TenantUsageMapper usageMapper;

    @GetMapping
    @Operation(summary = "查询当前租户机器人配额")
    @PreAuthorize("@ss.hasPermission('tenant:quota:query')")
    public CommonResult<TenantQuotaRespVO> getCurrentTenantQuota() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        var quota = quotaService.getQuota(tenantId);
        TenantUsageDO usage = usageMapper.selectByTenantId(tenantId);
        TenantQuotaRespVO response = new TenantQuotaRespVO();
        response.setTenantId(tenantId);
        response.setRobotLimit(quota.getRobotLimit());
        response.setRobotUsed(usage == null ? 0 : usage.getRobotUsed());
        return success(response);
    }

    @PutMapping("/{tenantId}")
    @Operation(summary = "更新租户机器人配额")
    @PreAuthorize("@ss.hasRole('super_admin') and @ss.hasPermission('tenant:quota:update')")
    public CommonResult<Boolean> updateQuota(@PathVariable long tenantId,
                                              @Valid @RequestBody TenantQuotaUpdateReqVO request) {
        quotaService.updateRobotLimit(tenantId, request.getRobotLimit());
        return success(true);
    }
}
