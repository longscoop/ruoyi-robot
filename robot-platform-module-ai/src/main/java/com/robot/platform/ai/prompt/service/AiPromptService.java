package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;

import java.util.List;

public interface AiPromptService {

    AiPromptDO create(CreatePromptCommand command);

    AiPromptDO get(long tenantId, long id);

    List<AiPromptDO> list(long tenantId);

    record CreatePromptCommand(long tenantId, String name, String code, String type,
                               String content, String status) {
    }
}
