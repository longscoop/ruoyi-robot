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
        return retrieve(query, limit, false);
    }

    @Override
    public List<MemorySnippet> retrieveBackground(MemoryQuery query, int limit) {
        return retrieve(query, limit, true);
    }

    private List<MemorySnippet> retrieve(MemoryQuery query, int limit, boolean background) {
        Objects.requireNonNull(query, "query");
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        List<AiMemoryDO> candidates = mapper.selectActiveCandidates(
                query.tenantId(), query.robotId(), query.memberId(), query.now());
        return candidates.stream()
                .filter(row -> background || relevant(row, query))
                .map(row -> toSnippet(row, query))
                .sorted(Comparator.comparingDouble(MemorySnippet::score).reversed()
                        .thenComparingLong(MemorySnippet::id))
                .limit(effectiveLimit)
                .collect(Collectors.toList());
    }

    private static boolean relevant(AiMemoryDO row, MemoryQuery query) {
        if (query.memoryType() != null && !query.memoryType().equalsIgnoreCase(row.getMemoryType())) return false;
        // Only communication preferences apply to every turn. Topics/pets/hobbies need a matching question.
        boolean standing = MemoryCategories.communicationPreference(row.getMemoryType(), row.getContent());
        return standing || MemorySelection.relevance(query.text(),
                new MemorySnippet(row.getId(), row.getScope(), row.getMemoryType(), row.getContent(), row.getSummary(), 0)) >= .20;
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
        Set<String> terms = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("[a-z0-9]+|[\\p{IsHan}]+").matcher(query.toLowerCase(Locale.ROOT));
        Set<String> stop = Set.of("用户", "喜欢", "家里", "里的", "叫什么", "叫什", "么吗", "还有", "还记", "记得", "什么", "这个", "那个", "我的", "你的", "告诉", "知道", "一下", "请问", "今天", "你好", "说说", "能不能", "可以");
        while (matcher.find()) {
            String word = matcher.group();
            if (word.matches("[a-z0-9]+")) terms.add(word);
            else if (word.length() == 1) {
                if (!"我你他她的是了在有和吗呢啊吧说好".contains(word)) terms.add(word);
            } else {
                // Standalone topic nouns otherwise disappear in phrases such as “家里的猫叫什么”.
                for (char topic : "猫狗茶糖鱼酒肉饭鸟花床车鞋球书奶".toCharArray())
                    if (word.indexOf(topic) >= 0) terms.add(String.valueOf(topic));
                for (int i = 0; i < word.length() - 1; i++) {
                    String pair = word.substring(i, i + 2);
                    if (!stop.contains(pair)) terms.add(pair);
                }
            }
        }
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
