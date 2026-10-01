package com.robot.platform.ai.memory.service;

import java.util.List;

public interface MemoryRetriever {
    List<MemorySnippet> retrieve(MemoryQuery query, int limit);

    /** Compatibility path for providers without per-turn instruction updates. */
    default List<MemorySnippet> retrieveBackground(MemoryQuery query, int limit) {
        return retrieve(query, limit);
    }

    default List<MemorySnippet> retrieve(MemoryQuery query) {
        return retrieve(query, 8);
    }
}
