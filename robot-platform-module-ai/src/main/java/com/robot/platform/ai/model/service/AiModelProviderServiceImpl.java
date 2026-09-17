package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.security.AiSecretCipher;
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
public class AiModelProviderServiceImpl implements AiModelProviderService {
    private static final Set<String> PROVIDER_TYPES = Set.of("QWEN", "DEEPSEEK", "DOUBAO");

    private final AiModelProviderMapper mapper;
    private final AiSecretCipher secretCipher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long create(CreateProviderCommand command) {
        requireCurrentTenant(command.tenantId());
        requireProviderType(command.providerType());
        if (isBlank(command.apiKey())) {
            throw exception(AI_PROVIDER_API_KEY_REQUIRED);
        }
        AiModelProviderDO row = AiModelProviderDO.builder()
                .tenantId(command.tenantId())
                .name(command.name())
                .code(command.code())
                .providerType(command.providerType())
                .baseUrl(command.baseUrl())
                .apiKeyCiphertext(secretCipher.encrypt(command.apiKey()))
                .configJson(command.configJson())
                .status(defaultStatus(command.status()))
                .build();
        mapper.insert(row);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateProviderCommand command) {
        requireCurrentTenant(command.tenantId());
        requireProviderType(command.providerType());
        AiModelProviderDO row = requireProvider(command.tenantId(), command.id());
        row.setName(command.name());
        row.setCode(command.code());
        row.setProviderType(command.providerType());
        row.setBaseUrl(command.baseUrl());
        row.setConfigJson(command.configJson());
        row.setStatus(defaultStatus(command.status()));
        if (!isBlank(command.apiKey())) {
            row.setApiKeyCiphertext(secretCipher.encrypt(command.apiKey()));
        }
        mapper.updateById(row);
    }

    @Override
    public AiModelProviderDO get(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        return requireProvider(tenantId, id);
    }

    @Override
    public List<AiModelProviderDO> list(long tenantId) {
        requireCurrentTenant(tenantId);
        return mapper.selectByTenantId(tenantId);
    }

    @Override
    public ResolvedProviderCredential resolveCredential(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        AiModelProviderDO row = requireProvider(tenantId, id);
        if (isBlank(row.getApiKeyCiphertext())) {
            throw exception(AI_PROVIDER_API_KEY_REQUIRED);
        }
        return new ResolvedProviderCredential(row.getId(), row.getTenantId(), row.getProviderType(), row.getBaseUrl(),
                secretCipher.decrypt(row.getApiKeyCiphertext()), row.getConfigJson());
    }

    private AiModelProviderDO requireProvider(long tenantId, long id) {
        AiModelProviderDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw exception(AI_PROVIDER_NOT_EXISTS);
        }
        return row;
    }

    private static void requireProviderType(String providerType) {
        if (!PROVIDER_TYPES.contains(providerType)) {
            throw exception(AI_PROVIDER_TYPE_INVALID);
        }
    }

    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) {
            throw exception(AI_TENANT_FORBIDDEN);
        }
    }

    private static String defaultStatus(String status) {
        return isBlank(status) ? "ENABLED" : status;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
