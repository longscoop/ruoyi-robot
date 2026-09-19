package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.security.AiSecretCipher;
import org.springframework.stereotype.Component;

@Component
public class ResolvedModelResolver {

    private static final String ENABLED = "ENABLED";

    private final AiModelMapper modelMapper;
    private final AiModelProviderMapper providerMapper;
    private final AiSecretCipher secretCipher;

    public ResolvedModelResolver(AiModelMapper modelMapper,
                                 AiModelProviderMapper providerMapper,
                                 AiSecretCipher secretCipher) {
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
        this.secretCipher = secretCipher;
    }

    public ResolvedModel resolve(long tenantId, long modelId) {
        if (tenantId <= 0 || modelId <= 0) {
            throw new IllegalArgumentException("tenantId and modelId must be positive");
        }
        AiModelDO model = modelMapper.selectByIdAndTenantId(modelId, tenantId);
        if (model == null || !ENABLED.equals(model.getStatus())) {
            throw new IllegalStateException("AI model is not enabled in current tenant");
        }
        AiModelProviderDO provider = providerMapper.selectByIdAndTenantId(model.getProviderId(), tenantId);
        if (provider == null || !ENABLED.equals(provider.getStatus())) {
            throw new IllegalStateException("AI model provider is not enabled in current tenant");
        }
        if (provider.getApiKeyCiphertext() == null || provider.getApiKeyCiphertext().isBlank()) {
            throw new IllegalStateException("AI model provider credential is not configured");
        }

        String credential = secretCipher.decrypt(provider.getApiKeyCiphertext());
        return new ResolvedModel(
                tenantId,
                model.getId(),
                provider.getId(),
                provider.getProviderType(),
                model.getModelType(),
                model.getModelCode(),
                provider.getBaseUrl(),
                provider.getConfigJson(),
                model.getConfigJson(),
                credential);
    }
}
