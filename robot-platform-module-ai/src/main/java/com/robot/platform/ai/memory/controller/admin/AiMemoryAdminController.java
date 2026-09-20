package com.robot.platform.ai.memory.controller.admin;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.service.AiMemoryAdminService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/admin-api/ai/memories")
@Validated
@RequiredArgsConstructor
public class AiMemoryAdminController {
    private final AiMemoryAdminService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:memory:query')")
    public CommonResult<List<MemoryRespVO>> list() {
        return success(service.list(TenantContextHolder.getRequiredTenantId()).stream().map(AiMemoryAdminController::toResp).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:memory:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody MemoryUpdateReqVO request) {
        service.update(TenantContextHolder.getRequiredTenantId(), id, request.getContent(), request.getSummary(),
                request.getImportance(), request.getExpiresAt());
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:memory:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        service.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    private static MemoryRespVO toResp(AiMemoryDO row) {
        return new MemoryRespVO(row.getId(), row.getScope(), row.getMemberId(), row.getRobotId(), row.getMemoryType(),
                row.getContent(), row.getSummary(), row.getImportance(), row.getConfidence(), row.getExpiresAt(),
                row.getStatus(), row.getCreatedAt(), row.getUpdatedAt());
    }

    @Data
    public static class MemoryUpdateReqVO {
        private String content;
        private String summary;
        private BigDecimal importance;
        private LocalDateTime expiresAt;
    }

    public record MemoryRespVO(Long id, String scope, Long memberId, Long robotId, String memoryType,
                               String content, String summary, BigDecimal importance, BigDecimal confidence,
                               LocalDateTime expiresAt, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
