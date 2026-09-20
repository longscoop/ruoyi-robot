package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;

public interface MemoryPipeline {
    void submit(MemoryExtractor.CompletedTurn turn, ConversationIdentity identity, AiAgentConfig agent);
}
