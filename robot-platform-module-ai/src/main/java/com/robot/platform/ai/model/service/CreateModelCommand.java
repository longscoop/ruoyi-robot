package com.robot.platform.ai.model.service;

public record CreateModelCommand(long tenantId, long providerId, String name, String modelCode,
                                 String modelType, String capabilitiesJson, String configJson, String status) {
}
