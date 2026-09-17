package com.robot.platform.ai.prompt.controller.admin;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.ai.prompt.service.CreatePromptCommand;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/admin-api/ai/prompts")
@Validated
@RequiredArgsConstructor
public class AiPromptAdminController {
    private final AiPromptService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:prompt:query')")
    public CommonResult<List<PromptRespVO>> listVersions(@RequestParam String code) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(service.listVersions(tenantId, code).stream().map(AiPromptAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:prompt:query')")
    public CommonResult<PromptRespVO> get(@PathVariable long id) {
        return success(toResp(service.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:prompt:create')")
    public CommonResult<PromptRespVO> create(@Valid @RequestBody PromptReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(toResp(service.create(new CreatePromptCommand(tenantId, request.getName(), request.getCode(),
                request.getType(), request.getContent(), request.getStatus()))));
    }

    private static PromptRespVO toResp(AiPromptDO row) {
        return new PromptRespVO(row.getId(), row.getName(), row.getCode(), row.getType(), row.getContent(),
                row.getVersion(), row.getStatus());
    }

    @Data
    public static class PromptReqVO {
        @NotBlank @Size(max = 128) private String name;
        @NotBlank @Size(max = 64) private String code;
        @NotBlank @Size(max = 32) private String type;
        @NotBlank private String content;
        private String status = "ENABLED";
    }

    public record PromptRespVO(long id, String name, String code, String type, String content,
                               int version, String status) { }
}
