package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.security.AiSecretCipher;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiModelProviderServiceImpl implements AiModelProviderService {
    private static final Set<String> SUPPORTED_PROVIDER_TYPES = Set.of("QWEN", "DEEPSEEK", "DOUBAO");
    private static final String DEFAULT_STATUS = "ENABLED";

    private final AiModelProviderMapper mapper;
    private final AiSecretCipher secretCipher;

    public AiModelProviderServiceImpl(AiModelProviderMapper mapper, AiSecretCipher secretCipher) {
        this.mapper = mapper;
        this.secretCipher = secretCipher;
    }

    @Override
    public ProviderView create(CreateProviderCommand command) {
        requireTenant(command.tenantId());
        String providerType = requireProviderType(command.providerType());
        requireText(command.name(), "Provider name must not be blank");
        requireText(command.code(), "Provider code must not be blank");
        requireText(command.baseUrl(), "Provider baseUrl must not be blank");

        AiModelProviderDO row = new AiModelProviderDO();
        row.setTenantId(command.tenantId());
        row.setName(command.name().trim());
        row.setCode(command.code().trim());
        row.setProviderType(providerType);
        row.setBaseUrl(command.baseUrl().trim());
        row.setApiKeyCiphertext(encryptIfPresent(command.apiKey()));
        row.setConfigJson(command.configJson());
        row.setStatus(DEFAULT_STATUS);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Provider code already exists in current tenant");
        }
        return toView(row);
    }

    @Override
    public ProviderView update(UpdateProviderCommand command) {
        requireTenant(command.tenantId());
        AiModelProviderDO row = requireProvider(command.tenantId(), command.id());
        String providerType = requireProviderType(command.providerType());
        requireText(command.name(), "Provider name must not be blank");
        requireText(command.code(), "Provider code must not be blank");
        requireText(command.baseUrl(), "Provider baseUrl must not be blank");

        row.setName(command.name().trim());
        row.setCode(command.code().trim());
        row.setProviderType(providerType);
        row.setBaseUrl(command.baseUrl().trim());
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            row.setApiKeyCiphertext(secretCipher.encrypt(command.apiKey()));
        }
        row.setConfigJson(command.configJson());
        row.setStatus(normalizeStatus(command.status()));
        try {
            mapper.updateById(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Provider code already exists in current tenant");
        }
        return toView(row);
    }

    @Override
    public ProviderView get(long tenantId, long id) {
        requireTenant(tenantId);
        return toView(requireProvider(tenantId, id));
    }

    @Override
    public List<ProviderView> list(long tenantId) {
        requireTenant(tenantId);
        return mapper.selectByTenantId(tenantId).stream().map(AiModelProviderServiceImpl::toView).toList();
    }

    @Override
    public void delete(long tenantId, long id) {
        requireTenant(tenantId);
        requireProvider(tenantId, id);
        mapper.logicalDeleteByIdAndTenantId(id, tenantId);
    }

    private AiModelProviderDO requireProvider(long tenantId, long id) {
        AiModelProviderDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI model provider does not exist");
        }
        return row;
    }

    private String encryptIfPresent(String apiKey) {
        return apiKey == null || apiKey.isBlank() ? null : secretCipher.encrypt(apiKey);
    }

    private static ProviderView toView(AiModelProviderDO row) {
        return new ProviderView(row.getId(), row.getTenantId(), row.getName(), row.getCode(), row.getProviderType(),
                row.getBaseUrl(), row.getConfigJson(), row.getStatus(),
                row.getApiKeyCiphertext() != null && !row.getApiKeyCiphertext().isBlank());
    }

    private static String requireProviderType(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            throw invalidParamException("Provider type must not be blank");
        }
        String normalized = providerType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDER_TYPES.contains(normalized)) {
            throw invalidParamException("Unsupported provider type: {}", normalized);
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
