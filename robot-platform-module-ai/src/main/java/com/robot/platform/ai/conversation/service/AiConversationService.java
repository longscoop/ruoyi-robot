package com.robot.platform.ai.conversation.service;

import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;
import com.robot.platform.ai.conversation.dal.dataobject.AiConversationMessageDO;

import java.util.List;

public interface AiConversationService {

    long startConversation(long tenantId, long agentId, long robotId, Long memberId, String channel);

    AiConversationDO getConversation(long tenantId, long id);

    void appendMessage(AiConversationMessage message);

    List<AiConversationMessageDO> listMessages(long tenantId, long conversationId);

    record AiConversationMessage(long tenantId, long conversationId, String turnId, String role,
                                 String content, Long modelId, Integer inputTokens, Integer outputTokens,
                                 Long latencyMs, String metadataJson) {
    }
}
