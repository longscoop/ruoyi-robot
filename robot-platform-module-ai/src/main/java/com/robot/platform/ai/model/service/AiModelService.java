package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;

import java.util.List;

public interface AiModelService {

    AiModelDO create(CreateModelCommand command);

    AiModelDO update(UpdateModelCommand command);

    AiModelDO get(long tenantId, long id);

    List<AiModelDO> list(long tenantId);

    void delete(long tenantId, long id);

    record CreateModelCommand(long tenantId, long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) {
    }

    record UpdateModelCommand(long tenantId, long id, long providerId, String name, String modelCode, String modelType,
                              String capabilitiesJson, String configJson, String status) {
    }
}
