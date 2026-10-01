package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;

public interface MemoryPipeline {
    default int messageThreshold(AiAgentConfig agent) { return 6; }
    default void submitBatch(java.util.List<MemoryExtractor.CompletedTurn> turns, ConversationIdentity identity, AiAgentConfig agent) {
        turns.forEach(turn -> submit(turn, identity, agent));
    }
    void submit(MemoryExtractor.CompletedTurn turn, ConversationIdentity identity, AiAgentConfig agent);
}
