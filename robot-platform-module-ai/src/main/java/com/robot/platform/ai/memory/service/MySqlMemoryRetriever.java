package com.robot.platform.ai.memory.service;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MySqlMemoryRetriever implements MemoryRetriever {
    private static final int DEFAULT_LIMIT = 8;
    private final AiMemoryMapper mapper;

    public MySqlMemoryRetriever(AiMemoryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<MemorySnippet> retrieve(MemoryQuery query, int limit) {
        Objects.requireNonNull(query, "query");
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        List<AiMemoryDO> candidates = mapper.selectActiveCandidates(
                query.tenantId(), query.robotId(), query.memberId(), query.now());
        return candidates.stream()
                .map(row -> toSnippet(row, query))
                .sorted(Comparator.comparingDouble(MemorySnippet::score).reversed()
                        .thenComparingLong(MemorySnippet::id))
                .limit(effectiveLimit)
                .collect(Collectors.toList());
    }

    private static MemorySnippet toSnippet(AiMemoryDO row, MemoryQuery query) {
        double text = textScore(query.text(), row.getContent(), row.getSummary());
        double type = query.memoryType() != null && query.memoryType().equalsIgnoreCase(row.getMemoryType()) ? 1.0 : 0.0;
        double importance = decimal(row.getImportance());
        double recency = recency(row.getLastObservedAt(), query.now());
        double score = 0.40 * text + 0.20 * type + 0.25 * importance + 0.15 * recency;
        return new MemorySnippet(row.getId(), row.getScope(), row.getMemoryType(),
                row.getContent(), row.getSummary(), score);
    }

    static double textScore(String query, String content, String summary) {
        if (query == null || query.isBlank()) return 0.0;
        String haystack = ((content == null ? "" : content) + " " + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);
        Set<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(s -> !s.isBlank()).collect(Collectors.toSet());
        if (terms.isEmpty()) return 0.0;
        long matches = terms.stream().filter(haystack::contains).count();
        return (double) matches / terms.size();
    }

    static double recency(LocalDateTime observedAt, LocalDateTime now) {
        if (observedAt == null) return 0.0;
        long days = Math.max(0, Duration.between(observedAt, now).toDays());
        return 1.0 / (1.0 + days / 30.0);
    }

    private static double decimal(BigDecimal value) {
        return value == null ? 0.0 : Math.max(0.0, Math.min(1.0, value.doubleValue()));
    }
}
