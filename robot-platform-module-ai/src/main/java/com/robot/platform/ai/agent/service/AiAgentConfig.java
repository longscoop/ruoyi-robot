package com.robot.platform.ai.agent.service;

public record AiAgentConfig(long agentId, long tenantId, String code, String systemPrompt,
                            long promptId, int promptVersion, String realtimeMode,
                            Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                            String voiceConfigJson, String memoryMode,
                            boolean memoryReadEnabled, boolean memoryWriteEnabled) {

    public AiAgentConfig(long agentId, long tenantId, String code, String systemPrompt,
                         long promptId, int promptVersion, String realtimeMode,
                         Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                         String memoryMode, boolean memoryReadEnabled, boolean memoryWriteEnabled) {
        this(agentId, tenantId, code, systemPrompt, promptId, promptVersion, realtimeMode,
                conversationModelId, realtimeModelId, asrModelId, ttsModelId,
                null, memoryMode, memoryReadEnabled, memoryWriteEnabled);
    }
}
