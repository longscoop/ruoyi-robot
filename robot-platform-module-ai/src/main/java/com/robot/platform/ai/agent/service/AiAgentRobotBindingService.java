package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;

import java.util.List;

public interface AiAgentRobotBindingService {
    long bind(long tenantId, long agentId, long robotId, boolean makeDefault);
    void unbind(long tenantId, long agentId, long robotId);
    void setDefault(long tenantId, long agentId, long robotId);
    List<AiAgentRobotDO> listByRobot(long tenantId, long robotId);
    AiAgentRobotDO getDefault(long tenantId, long robotId);
    AiAgentDO requireDefaultAgent(long tenantId, long robotId);
    AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode);
}
