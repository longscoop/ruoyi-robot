package com.robot.platform.ai.model.service;

public record UpdateProviderCommand(long tenantId, long id, String name, String code, String providerType,
                                    String baseUrl, String apiKey, String configJson, String status) {
}
