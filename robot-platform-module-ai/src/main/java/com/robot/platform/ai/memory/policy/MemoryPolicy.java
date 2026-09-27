package com.robot.platform.ai.memory.policy;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.extract.MemoryCandidate;
import com.robot.platform.ai.memory.identity.ConversationIdentity;

import java.util.List;

public interface MemoryPolicy {
    MemoryDecision decide(MemoryCandidate candidate, ConversationIdentity identity,
                          MemoryDirectiveParser.Directive directive, List<AiMemoryDO> activeMemories);
}
