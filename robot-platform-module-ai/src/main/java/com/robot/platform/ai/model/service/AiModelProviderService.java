package com.robot.platform.ai.model.service;

import java.util.List;

public interface AiModelProviderService {

    ProviderView create(CreateProviderCommand command);

    ProviderView update(UpdateProviderCommand command);

    ProviderView get(long tenantId, long id);

    List<ProviderView> list(long tenantId);

    void delete(long tenantId, long id);

    record CreateProviderCommand(long tenantId, String name, String code, String providerType,
                                 String baseUrl, String apiKey, String configJson) {
    }

    record UpdateProviderCommand(long tenantId, long id, String name, String code, String providerType,
                                 String baseUrl, String apiKey, String configJson, String status) {
    }

    record ProviderView(Long id, Long tenantId, String name, String code, String providerType,
                        String baseUrl, String configJson, String status, boolean apiKeyConfigured) {
    }
}
