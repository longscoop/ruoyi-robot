package com.robot.platform.ai.model.service;

public record UpdateModelCommand(long tenantId, long id, long providerId, String name, String modelCode,
                                 String modelType, String capabilitiesJson, String configJson, String status) {
}
