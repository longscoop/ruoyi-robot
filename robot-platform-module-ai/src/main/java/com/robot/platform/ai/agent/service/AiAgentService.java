package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;

import java.util.List;

public interface AiAgentService {
    long create(CreateAgentCommand command);
    void update(UpdateAgentCommand command);
    void delete(long tenantId, long id);
    AiAgentDO get(long tenantId, long id);
    List<AiAgentDO> list(long tenantId);
    AiAgentConfig getResolvedConfig(long tenantId, long agentId);
}
