package com.robot.platform.ai.memory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemoryWrite(long tenantId, String scope, Long memberId, Long robotId,
                          String memoryType, String content, String summary,
                          BigDecimal importance, BigDecimal confidence,
                          Long sourceConversationId, Long sourceMessageId,
                          LocalDateTime firstObservedAt, LocalDateTime lastObservedAt,
                          LocalDateTime expiresAt) {
}
