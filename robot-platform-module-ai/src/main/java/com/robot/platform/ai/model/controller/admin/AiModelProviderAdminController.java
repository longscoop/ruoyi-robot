package com.robot.platform.ai.model.controller.admin;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.service.AiModelProviderService;
import com.robot.platform.ai.model.service.CreateProviderCommand;
import com.robot.platform.ai.model.service.UpdateProviderCommand;
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
@RequestMapping("/admin-api/ai/providers")
@Validated
@RequiredArgsConstructor
public class AiModelProviderAdminController {
    private final AiModelProviderService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:provider:query')")
    public CommonResult<List<ProviderRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(service.list(tenantId).stream().map(AiModelProviderAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:query')")
    public CommonResult<ProviderRespVO> get(@PathVariable long id) {
        return success(toResp(service.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:provider:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProviderReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(service.create(new CreateProviderCommand(tenantId, request.getName(), request.getCode(),
                request.getProviderType(), request.getBaseUrl(), request.getApiKey(), request.getConfigJson(), request.getStatus())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody ProviderReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        service.update(new UpdateProviderCommand(tenantId, id, request.getName(), request.getCode(),
                request.getProviderType(), request.getBaseUrl(), request.getApiKey(), request.getConfigJson(), request.getStatus()));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        service.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    private static ProviderRespVO toResp(AiModelProviderDO row) {
        return new ProviderRespVO(row.getId(), row.getName(), row.getCode(), row.getProviderType(), row.getBaseUrl(),
                row.getConfigJson(), row.getStatus(), row.getApiKeyCiphertext() != null && !row.getApiKeyCiphertext().isBlank());
    }

    @Data
    public static class ProviderReqVO {
        @NotBlank @Size(max = 128) private String name;
        @NotBlank @Size(max = 64) private String code;
        @NotBlank @Size(max = 32) private String providerType;
        @NotBlank @Size(max = 512) private String baseUrl;
        private String apiKey;
        private String configJson;
        private String status = "ENABLED";
    }

    public record ProviderRespVO(long id, String name, String code, String providerType, String baseUrl,
                                 String configJson, String status, boolean apiKeyConfigured) { }
}
