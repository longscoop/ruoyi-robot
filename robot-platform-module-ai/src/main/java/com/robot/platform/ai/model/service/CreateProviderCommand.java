package com.robot.platform.ai.model.service;

public record CreateProviderCommand(long tenantId, String name, String code, String providerType,
                                    String baseUrl, String apiKey, String configJson, String status) {
}
