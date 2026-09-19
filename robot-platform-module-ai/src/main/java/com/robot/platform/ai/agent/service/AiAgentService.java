package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;

import java.util.List;

public interface AiAgentService {

    AiAgentDO create(CreateAgentCommand command);

    AiAgentDO update(UpdateAgentCommand command);

    AiAgentDO get(long tenantId, long id);

    List<AiAgentDO> list(long tenantId);

    void delete(long tenantId, long id);

    AiAgentConfig getResolvedConfig(long tenantId, long agentId);

    record CreateAgentCommand(long tenantId, String name, String code, String description, long systemPromptId,
                              Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                              String realtimeMode, String memoryMode, boolean memoryReadEnabled,
                              boolean memoryWriteEnabled, boolean knowledgeEnabled,
                              String voiceConfigJson, String status) {
    }

    record UpdateAgentCommand(long tenantId, long id, String name, String code, String description,
                              long systemPromptId, Long conversationModelId, Long realtimeModelId,
                              Long asrModelId, Long ttsModelId, String realtimeMode, String memoryMode,
                              boolean memoryReadEnabled, boolean memoryWriteEnabled, boolean knowledgeEnabled,
                              String voiceConfigJson, String status) {
    }
}
