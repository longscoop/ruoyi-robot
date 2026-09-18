package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;

public interface AiAgentRobotBindingService {

    long bind(long tenantId, long agentId, long robotId, boolean defaultAgent);

    AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode);

    AiAgentDO requireDefaultAgent(long tenantId, long robotId);
}
