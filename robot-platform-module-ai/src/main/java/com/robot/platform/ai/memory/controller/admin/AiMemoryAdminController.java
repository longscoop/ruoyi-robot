package com.robot.platform.ai.memory.controller.admin;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.service.AiMemoryAdminService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/ai/memories")
@RequiredArgsConstructor
public class AiMemoryAdminController {

    private final AiMemoryAdminService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:memory:query')")
    public CommonResult<List<AiMemoryDO>> list() {
        return success(service.list(TenantContextHolder.getRequiredTenantId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:memory:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @RequestBody UpdateReq request) {
        service.update(TenantContextHolder.getRequiredTenantId(), id, request.content(),
                request.summary(), request.importance(), request.expiresAt());
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:memory:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        service.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    public record UpdateReq(String content, String summary, BigDecimal importance,
                            LocalDateTime expiresAt) {
    }
}
