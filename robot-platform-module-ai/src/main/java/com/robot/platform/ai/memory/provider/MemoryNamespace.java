package com.robot.platform.ai.memory.provider;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.identity.ConversationIdentity;

public final class MemoryNamespace {
    private MemoryNamespace() { }
    public static String of(ConversationIdentity identity, AiAgentConfig agent) {
        if (identity.tenantId() <= 0 || identity.robotId() <= 0 || agent.agentId() <= 0
                || identity.tenantId() != agent.tenantId()
                || (identity.memberMemoryAllowed() && (identity.memberId() == null || identity.memberId() <= 0))) throw new IllegalArgumentException("Memory tenant mismatch");
        String owner = identity.memberMemoryAllowed() && identity.memberId() != null
                ? "member-" + identity.memberId() + "-robot-" + identity.robotId() : "robot-" + identity.robotId();
        return "tenant-" + identity.tenantId() + "-agent-" + agent.agentId() + "-" + owner;
    }
}
