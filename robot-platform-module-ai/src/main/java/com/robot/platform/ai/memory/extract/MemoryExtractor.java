package com.robot.platform.ai.memory.extract;

import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;

import java.util.List;

public interface MemoryExtractor {
    List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
                                  MemoryDirectiveParser.Directive directive);

    record CompletedTurn(String userText, String assistantText) {
    }
}
