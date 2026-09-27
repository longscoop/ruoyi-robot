package com.robot.platform.ai.memory.extract;

import java.time.LocalDateTime;

public record MemoryCandidate(String scope, String memoryType, String content,
                              double importance, double confidence, LocalDateTime expiresAt) {
}
