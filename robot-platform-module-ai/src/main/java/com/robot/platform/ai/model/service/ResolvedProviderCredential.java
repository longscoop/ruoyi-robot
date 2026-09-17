package com.robot.platform.ai.model.service;

public record ResolvedProviderCredential(long providerId, long tenantId, String providerType, String baseUrl,
                                         String apiKey, String configJson) {
    @Override
    public String toString() {
        return "ResolvedProviderCredential[providerId=" + providerId + ", tenantId=" + tenantId
                + ", providerType=" + providerType + ", baseUrl=" + baseUrl
                + ", apiKey=<redacted>, configJson=" + configJson + "]";
    }
}
