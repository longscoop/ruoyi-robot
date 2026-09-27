package com.robot.platform.ai.memory.service;

public record MemorySnippet(long id, String scope, String memoryType,
                            String content, String summary, double score) {
}
