package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiModelServiceImpl implements AiModelService {
    private static final Set<String> SUPPORTED_MODEL_TYPES =
            Set.of("CHAT", "REALTIME_S2S", "ASR", "TTS", "EMBEDDING");
    private static final String DEFAULT_STATUS = "ENABLED";

    private final AiModelMapper modelMapper;
    private final AiModelProviderMapper providerMapper;

    public AiModelServiceImpl(AiModelMapper modelMapper, AiModelProviderMapper providerMapper) {
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
    }

    @Override
    public AiModelDO create(CreateModelCommand command) {
        requireTenant(command.tenantId());
        String modelType = requireModelType(command.modelType());
        requireText(command.name(), "Model name must not be blank");
        requireText(command.modelCode(), "Model code must not be blank");
        requireOwnedProvider(command.tenantId(), command.providerId());

        AiModelDO row = new AiModelDO();
        row.setTenantId(command.tenantId());
        row.setProviderId(command.providerId());
        row.setName(command.name().trim());
        row.setModelCode(command.modelCode().trim());
        row.setModelType(modelType);
        row.setCapabilitiesJson(command.capabilitiesJson());
        row.setConfigJson(command.configJson());
        row.setStatus(normalizeStatus(command.status()));
        try {
            modelMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Model code already exists for provider in current tenant");
        }
        return row;
    }

    @Override
    public AiModelDO update(UpdateModelCommand command) {
        requireTenant(command.tenantId());
        AiModelDO row = requireModel(command.tenantId(), command.id());
        String modelType = requireModelType(command.modelType());
        requireText(command.name(), "Model name must not be blank");
        requireText(command.modelCode(), "Model code must not be blank");
        requireOwnedProvider(command.tenantId(), command.providerId());

        row.setProviderId(command.providerId());
        row.setName(command.name().trim());
        row.setModelCode(command.modelCode().trim());
        row.setModelType(modelType);
        row.setCapabilitiesJson(command.capabilitiesJson());
        row.setConfigJson(command.configJson());
        row.setStatus(normalizeStatus(command.status()));
        try {
            modelMapper.updateById(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Model code already exists for provider in current tenant");
        }
        return row;
    }

    @Override
    public AiModelDO get(long tenantId, long id) {
        requireTenant(tenantId);
        return requireModel(tenantId, id);
    }

    @Override
    public List<AiModelDO> list(long tenantId) {
        requireTenant(tenantId);
        return modelMapper.selectByTenantId(tenantId);
    }

    @Override
    public void delete(long tenantId, long id) {
        requireTenant(tenantId);
        requireModel(tenantId, id);
        modelMapper.logicalDeleteByIdAndTenantId(id, tenantId);
    }

    private AiModelDO requireModel(long tenantId, long id) {
        AiModelDO row = modelMapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI model does not exist");
        }
        return row;
    }

    private void requireOwnedProvider(long tenantId, long providerId) {
        AiModelProviderDO provider = providerMapper.selectByIdAndTenantId(providerId, tenantId);
        if (provider == null) {
            throw invalidParamException("AI model provider does not exist in current tenant");
        }
    }

    private static String requireModelType(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            throw invalidParamException("Model type must not be blank");
        }
        String normalized = modelType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_MODEL_TYPES.contains(normalized)) {
            throw invalidParamException("Unsupported model type: {}", normalized);
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? DEFAULT_STATUS : status.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalidParamException(message);
        }
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }
}
