package com.robot.platform.ai.model.controller.admin;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.model.service.CreateModelCommand;
import com.robot.platform.ai.model.service.UpdateModelCommand;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/admin-api/ai/models")
@Validated
@RequiredArgsConstructor
public class AiModelAdminController {
    private final AiModelService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<List<ModelRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(service.list(tenantId).stream().map(AiModelAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<ModelRespVO> get(@PathVariable long id) {
        return success(toResp(service.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:model:create')")
    public CommonResult<Long> create(@Valid @RequestBody ModelReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(service.create(new CreateModelCommand(tenantId, request.getProviderId(), request.getName(),
                request.getModelCode(), request.getModelType(), request.getCapabilitiesJson(), request.getConfigJson(),
                request.getStatus())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody ModelReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        service.update(new UpdateModelCommand(tenantId, id, request.getProviderId(), request.getName(),
                request.getModelCode(), request.getModelType(), request.getCapabilitiesJson(), request.getConfigJson(),
                request.getStatus()));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        service.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    private static ModelRespVO toResp(AiModelDO row) {
        return new ModelRespVO(row.getId(), row.getProviderId(), row.getName(), row.getModelCode(), row.getModelType(),
                row.getCapabilitiesJson(), row.getConfigJson(), row.getStatus());
    }

    @Data
    public static class ModelReqVO {
        @NotNull private Long providerId;
        @NotBlank @Size(max = 128) private String name;
        @NotBlank @Size(max = 128) private String modelCode;
        @NotBlank @Size(max = 32) private String modelType;
        private String capabilitiesJson;
        private String configJson;
        private String status = "ENABLED";
    }

    public record ModelRespVO(long id, long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) { }
}
