package com.robot.platform.ai.memory.service;

import java.time.LocalDateTime;

public record MemoryQuery(long tenantId, long robotId, Long memberId,
                          String text, String memoryType, LocalDateTime now) {
    public MemoryQuery {
        if (tenantId <= 0 || robotId <= 0) {
            throw new IllegalArgumentException("tenantId and robotId must be positive");
        }
        if (memberId != null && memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        now = now == null ? LocalDateTime.now() : now;
    }
}
