package com.robot.platform.ai.model.service;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;

import java.util.List;

public interface AiModelProviderService {
    long create(CreateProviderCommand command);
    void update(UpdateProviderCommand command);
    AiModelProviderDO get(long tenantId, long id);
    List<AiModelProviderDO> list(long tenantId);
    ResolvedProviderCredential resolveCredential(long tenantId, long id);
}
