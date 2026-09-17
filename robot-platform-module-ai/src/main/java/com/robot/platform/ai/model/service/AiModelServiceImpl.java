package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.robot.platform.ai.enums.AiErrorCodeConstants.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {
    private static final Set<String> MODEL_TYPES = Set.of("CHAT", "REALTIME_S2S", "ASR", "TTS", "EMBEDDING");

    private final AiModelMapper mapper;
    private final AiModelProviderMapper providerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long create(CreateModelCommand command) {
        requireCurrentTenant(command.tenantId());
        requireProvider(command.tenantId(), command.providerId());
        requireModelType(command.modelType());
        AiModelDO row = AiModelDO.builder()
                .tenantId(command.tenantId())
                .providerId(command.providerId())
                .name(command.name())
                .modelCode(command.modelCode())
                .modelType(command.modelType())
                .capabilitiesJson(command.capabilitiesJson())
                .configJson(command.configJson())
                .status(defaultStatus(command.status()))
                .build();
        mapper.insert(row);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateModelCommand command) {
        requireCurrentTenant(command.tenantId());
        requireProvider(command.tenantId(), command.providerId());
        requireModelType(command.modelType());
        AiModelDO row = requireModel(command.tenantId(), command.id());
        row.setProviderId(command.providerId());
        row.setName(command.name());
        row.setModelCode(command.modelCode());
        row.setModelType(command.modelType());
        row.setCapabilitiesJson(command.capabilitiesJson());
        row.setConfigJson(command.configJson());
        row.setStatus(defaultStatus(command.status()));
        mapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        AiModelDO row = requireModel(tenantId, id);
        mapper.deleteById(row.getId());
    }

    @Override
    public AiModelDO get(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        return requireModel(tenantId, id);
    }

    @Override
    public List<AiModelDO> list(long tenantId) {
        requireCurrentTenant(tenantId);
        return mapper.selectByTenantId(tenantId);
    }

    @Override
    public AiModelDO requireType(long tenantId, long id, String expectedType) {
        requireCurrentTenant(tenantId);
        AiModelDO model = requireModel(tenantId, id);
        if (!expectedType.equals(model.getModelType())) {
            throw exception(AI_MODEL_TYPE_INVALID);
        }
        return model;
    }

    private AiModelDO requireModel(long tenantId, long id) {
        AiModelDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw exception(AI_MODEL_NOT_EXISTS);
        }
        return row;
    }

    private void requireProvider(long tenantId, long providerId) {
        if (providerMapper.selectByIdAndTenantId(providerId, tenantId) == null) {
            throw exception(AI_PROVIDER_NOT_EXISTS);
        }
    }

    private static void requireModelType(String modelType) {
        if (!MODEL_TYPES.contains(modelType)) {
            throw exception(AI_MODEL_TYPE_INVALID);
        }
    }

    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) {
            throw exception(AI_TENANT_FORBIDDEN);
        }
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status;
    }
}
