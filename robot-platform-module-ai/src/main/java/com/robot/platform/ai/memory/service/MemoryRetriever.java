package com.robot.platform.ai.memory.service;

import java.util.List;

public interface MemoryRetriever {
    List<MemorySnippet> retrieve(MemoryQuery query, int limit);

    default List<MemorySnippet> retrieve(MemoryQuery query) {
        return retrieve(query, 8);
    }
}
