package com.robot.platform.ai.prompt.controller.admin;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
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

    private final AiPromptService promptService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:prompt:query')")
    public CommonResult<List<PromptRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(promptService.list(tenantId).stream().map(AiPromptAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:prompt:query')")
    public CommonResult<PromptRespVO> get(@PathVariable long id) {
        return success(toResp(promptService.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:prompt:create')")
    public CommonResult<Long> create(@Valid @RequestBody PromptCreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        AiPromptDO prompt = promptService.create(new AiPromptService.CreatePromptCommand(
                tenantId, request.getName(), request.getCode(), request.getType(),
                request.getContent(), request.getStatus()));
        return success(prompt.getId());
    }

    private static PromptRespVO toResp(AiPromptDO prompt) {
        return new PromptRespVO(prompt.getId(), prompt.getName(), prompt.getCode(), prompt.getType(),
                prompt.getContent(), prompt.getVersion(), prompt.getStatus());
    }

    @Data
    public static class PromptCreateReqVO {
        private String name;
        private String code;
        private String type;
        private String content;
        private String status;
    }

    public record PromptRespVO(Long id, String name, String code, String type,
                               String content, Integer version, String status) {
    }
}
