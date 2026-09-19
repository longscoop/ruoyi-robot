package com.robot.platform.ai.model.controller.admin;

import com.robot.platform.ai.model.service.AiModelProviderService;
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
@RequestMapping("/admin-api/ai/providers")
@Validated
@RequiredArgsConstructor
public class AiModelProviderAdminController {

    private final AiModelProviderService providerService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:provider:query')")
    public CommonResult<List<ProviderRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(providerService.list(tenantId).stream().map(AiModelProviderAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:query')")
    public CommonResult<ProviderRespVO> get(@PathVariable long id) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(toResp(providerService.get(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:provider:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProviderCreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        var provider = providerService.create(new AiModelProviderService.CreateProviderCommand(
                tenantId, request.getName(), request.getCode(), request.getProviderType(),
                request.getBaseUrl(), request.getApiKey(), request.getConfigJson()));
        return success(provider.id());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody ProviderUpdateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        providerService.update(new AiModelProviderService.UpdateProviderCommand(
                tenantId, id, request.getName(), request.getCode(), request.getProviderType(),
                request.getBaseUrl(), request.getApiKey(), request.getConfigJson(), request.getStatus()));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:provider:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        providerService.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    private static ProviderRespVO toResp(AiModelProviderService.ProviderView provider) {
        return new ProviderRespVO(provider.id(), provider.name(), provider.code(), provider.providerType(),
                provider.baseUrl(), provider.configJson(), provider.status(), provider.apiKeyConfigured());
    }

    @Data
    public static class ProviderCreateReqVO {
        private String name;
        private String code;
        private String providerType;
        private String baseUrl;
        private String apiKey;
        private String configJson;
    }

    @Data
    public static class ProviderUpdateReqVO {
        private String name;
        private String code;
        private String providerType;
        private String baseUrl;
        private String apiKey;
        private String configJson;
        private String status;
    }

    public record ProviderRespVO(Long id, String name, String code, String providerType,
                                 String baseUrl, String configJson, String status,
                                 boolean apiKeyConfigured) {
    }
}
