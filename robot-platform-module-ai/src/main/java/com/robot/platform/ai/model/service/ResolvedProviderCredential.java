package com.robot.platform.ai.model.service;

public record ResolvedProviderCredential(long providerId, long tenantId, String providerType, String baseUrl,
                                         String apiKey, String configJson) {
}
