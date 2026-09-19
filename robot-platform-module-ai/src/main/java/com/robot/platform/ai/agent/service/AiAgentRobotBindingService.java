package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;

import java.util.List;

public interface AiAgentRobotBindingService {

    long bind(long tenantId, long agentId, long robotId, boolean defaultAgent);

    List<AiAgentRobotDO> list(long tenantId, long agentId);

    void unbind(long tenantId, long agentId, long robotId);

    AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode);

    AiAgentDO requireDefaultAgent(long tenantId, long robotId);
}
