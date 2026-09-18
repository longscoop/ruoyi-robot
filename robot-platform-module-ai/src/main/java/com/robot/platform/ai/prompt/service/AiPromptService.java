package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;

public interface AiPromptService {

    AiPromptDO create(CreatePromptCommand command);

    AiPromptDO get(long tenantId, long id);

    record CreatePromptCommand(long tenantId, String name, String code, String type,
                               String content, String status) {
    }
}
