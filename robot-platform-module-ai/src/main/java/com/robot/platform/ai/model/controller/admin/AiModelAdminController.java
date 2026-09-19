package com.robot.platform.ai.model.controller.admin;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.service.AiModelService;
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
@RequestMapping("/admin-api/ai/models")
@Validated
@RequiredArgsConstructor
public class AiModelAdminController {

    private final AiModelService modelService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<List<ModelRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(modelService.list(tenantId).stream().map(AiModelAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<ModelRespVO> get(@PathVariable long id) {
        return success(toResp(modelService.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:model:create')")
    public CommonResult<Long> create(@Valid @RequestBody ModelCreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        AiModelDO model = modelService.create(new AiModelService.CreateModelCommand(
                tenantId, request.getProviderId(), request.getName(), request.getModelCode(),
                request.getModelType(), request.getCapabilitiesJson(), request.getConfigJson(), request.getStatus()));
        return success(model.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody ModelUpdateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        modelService.update(new AiModelService.UpdateModelCommand(
                tenantId, id, request.getProviderId(), request.getName(), request.getModelCode(),
                request.getModelType(), request.getCapabilitiesJson(), request.getConfigJson(), request.getStatus()));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:model:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        modelService.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    private static ModelRespVO toResp(AiModelDO model) {
        return new ModelRespVO(model.getId(), model.getProviderId(), model.getName(), model.getModelCode(),
                model.getModelType(), model.getCapabilitiesJson(), model.getConfigJson(), model.getStatus());
    }

    @Data
    public static class ModelCreateReqVO {
        private Long providerId;
        private String name;
        private String modelCode;
        private String modelType;
        private String capabilitiesJson;
        private String configJson;
        private String status;
    }

    @Data
    public static class ModelUpdateReqVO {
        private Long providerId;
        private String name;
        private String modelCode;
        private String modelType;
        private String capabilitiesJson;
        private String configJson;
        private String status;
    }

    public record ModelRespVO(Long id, Long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) {
    }
}
