package com.robot.platform.ai.memory.service;

public interface MemoryStore {
    long save(MemoryWrite write);
    void supersede(long tenantId, long memoryId, Long replacementId);
    void delete(long tenantId, long memoryId);
}
