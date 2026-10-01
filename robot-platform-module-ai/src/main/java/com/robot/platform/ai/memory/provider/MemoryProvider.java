package com.robot.platform.ai.memory.provider;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.MemorySnippet;
import java.util.List;

/** A provider owns query, extraction/summarization and storage; runtime owns session history. */
public interface MemoryProvider {
    String name();
    List<MemorySnippet> queryMemory(ConversationIdentity identity, AiAgentConfig agent, String question, int limit);
    void saveMemory(ConversationIdentity identity, AiAgentConfig agent, List<CompletedTurn> messages);
    default boolean configured(long tenantId) { return true; }
}
