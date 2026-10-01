package com.robot.platform.ai.memory.extract;

import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;

import java.util.List;

public interface MemoryExtractor {
    List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
                                  MemoryDirectiveParser.Directive directive);

    default List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
            MemoryDirectiveParser.Directive directive, com.robot.platform.ai.agent.service.AiAgentConfig agent) {
        return extract(turn, identity, directive);
    }

    record CompletedTurn(String userText, String assistantText, Long conversationId) {
        public CompletedTurn(String userText, String assistantText) { this(userText, assistantText, null); }
    }
}
