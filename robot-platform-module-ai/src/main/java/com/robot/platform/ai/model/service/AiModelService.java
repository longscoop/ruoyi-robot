package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;

public interface AiModelService {

    AiModelDO create(CreateModelCommand command);

    AiModelDO update(UpdateModelCommand command);

    AiModelDO get(long tenantId, long id);

    record CreateModelCommand(long tenantId, long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) {
    }

    record UpdateModelCommand(long tenantId, long id, long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) {
    }
}
