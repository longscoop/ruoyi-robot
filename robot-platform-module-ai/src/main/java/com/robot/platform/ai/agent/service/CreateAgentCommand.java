package com.robot.platform.ai.agent.service;

public record CreateAgentCommand(long tenantId, String name, String code, String description, long systemPromptId,
                                 Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                                 String realtimeMode, String memoryMode, boolean memoryReadEnabled,
                                 boolean memoryWriteEnabled, boolean knowledgeEnabled, String voiceConfigJson,
                                 String status) {
}
