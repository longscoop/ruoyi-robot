package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;

import java.util.List;

public interface AiPromptService {
    AiPromptDO create(CreatePromptCommand command);
    AiPromptDO get(long tenantId, long id);
    List<AiPromptDO> listVersions(long tenantId, String code);
}
