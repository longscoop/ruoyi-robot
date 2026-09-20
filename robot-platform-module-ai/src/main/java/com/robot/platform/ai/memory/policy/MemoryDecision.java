package com.robot.platform.ai.memory.policy;

public record MemoryDecision(Action action, Long existingMemoryId, String reason) {
    public enum Action { IGNORE, CREATE, REPLACE, DELETE_MATCHES }

    public static MemoryDecision ignore(String reason) { return new MemoryDecision(Action.IGNORE, null, reason); }
    public static MemoryDecision create(String reason) { return new MemoryDecision(Action.CREATE, null, reason); }
    public static MemoryDecision replace(long id, String reason) { return new MemoryDecision(Action.REPLACE, id, reason); }
    public static MemoryDecision deleteMatches(String reason) { return new MemoryDecision(Action.DELETE_MATCHES, null, reason); }
}
