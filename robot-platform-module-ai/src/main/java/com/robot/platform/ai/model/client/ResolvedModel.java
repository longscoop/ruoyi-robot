package com.robot.platform.ai.model.client;

import java.util.Locale;
import java.util.Objects;

public final class ResolvedModel {

    private final long tenantId;
    private final long modelId;
    private final long providerId;
    private final String providerType;
    private final String modelType;
    private final String modelCode;
    private final String baseUrl;
    private final String providerConfigJson;
    private final String modelConfigJson;
    private final String credential;

    public ResolvedModel(long tenantId, long modelId, long providerId,
                         String providerType, String modelType, String modelCode,
                         String baseUrl, String providerConfigJson, String modelConfigJson,
                         String credential) {
        if (tenantId <= 0 || modelId <= 0 || providerId <= 0) {
            throw new IllegalArgumentException("tenantId, modelId and providerId must be positive");
        }
        this.tenantId = tenantId;
        this.modelId = modelId;
        this.providerId = providerId;
        this.providerType = normalize(providerType, "providerType");
        this.modelType = normalize(modelType, "modelType");
        this.modelCode = requireText(modelCode, "modelCode");
        this.baseUrl = requireText(baseUrl, "baseUrl");
        this.providerConfigJson = providerConfigJson;
        this.modelConfigJson = modelConfigJson;
        this.credential = credential;
    }

    public long tenantId() {
        return tenantId;
    }

    public long modelId() {
        return modelId;
    }

    public long providerId() {
        return providerId;
    }

    public String providerType() {
        return providerType;
    }

    public String modelType() {
        return modelType;
    }

    public String modelCode() {
        return modelCode;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String providerConfigJson() {
        return providerConfigJson;
    }

    public String modelConfigJson() {
        return modelConfigJson;
    }

    public String credential() {
        return credential;
    }

    @Override
    public String toString() {
        return "ResolvedModel[" +
                "tenantId=" + tenantId +
                ", modelId=" + modelId +
                ", providerId=" + providerId +
                ", providerType=" + providerType +
                ", modelType=" + modelType +
                ", modelCode=" + modelCode +
                ", baseUrl=" + baseUrl +
                ", providerConfigJson=" + providerConfigJson +
                ", modelConfigJson=" + modelConfigJson +
                ", credential=[REDACTED]]";
    }

    private static String normalize(String value, String label) {
        return requireText(value, label).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
