package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;

import java.util.List;

public interface AiModelService {
    long create(CreateModelCommand command);
    void update(UpdateModelCommand command);
    void delete(long tenantId, long id);
    AiModelDO get(long tenantId, long id);
    List<AiModelDO> list(long tenantId);
    AiModelDO requireType(long tenantId, long id, String expectedType);
}
