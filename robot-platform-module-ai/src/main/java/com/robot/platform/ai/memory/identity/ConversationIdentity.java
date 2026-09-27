package com.robot.platform.ai.memory.identity;

public record ConversationIdentity(long tenantId, long robotId, Long memberId,
                                   String identityType, double confidence,
                                   boolean memberMemoryAllowed) {

    public static ConversationIdentity anonymous(long tenantId, long robotId) {
        return new ConversationIdentity(tenantId, robotId, null, "ANONYMOUS", 0.0, false);
    }
}
