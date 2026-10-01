package com.robot.platform.ai.memory.service;

import java.util.*;

/** One relevance gate for every store. Provider scores are only trusted for semantic search. */
public final class MemorySelection {
    private MemorySelection() { }

    public static List<MemorySnippet> select(String question, List<MemorySnippet> rows, int limit, boolean semantic) {
        if (rows == null) return List.of();
        Map<String, MemorySnippet> unique = new LinkedHashMap<>();
        for (MemorySnippet row : rows) {
            if (row == null || row.content() == null || row.content().isBlank()) continue;
            boolean standing = MemoryCategories.communicationPreference(row.memoryType(), row.content());
            double relevance = relevance(question, row);
            if (!standing && relevance < .20 && !(semantic && question != null && !question.isBlank()
                    && row.score() >= .70 && row.score() <= 1)) continue;
            String key = row.content().replaceAll("[\\p{P}\\p{Z}\\s]", "").toLowerCase(Locale.ROOT);
            double score = standing ? 2 : relevance + (semantic ? Math.max(0, row.score()) * .1 : 0);
            MemorySnippet selected = new MemorySnippet(row.id(), row.scope(), row.memoryType(), row.content(), row.summary(), score);
            unique.merge(key, selected, (a, b) -> a.score() >= b.score() ? a : b);
        }
        return unique.values().stream().sorted(Comparator.comparingDouble(MemorySnippet::score).reversed())
                .limit(Math.max(0, limit)).toList();
    }

    public static double relevance(String question, MemorySnippet row) {
        if (question == null || question.isBlank()) return 0;
        for (String topic : List.of("猫", "狗", "咖啡", "茶", "过敏", "机器人")) {
            if (question.contains(topic) && row.content().contains(topic)) return .75;
        }
        if (question.matches(".*(饮料|喝什么|什么茶).*") && row.content().matches(".*(茶|咖啡|牛奶|果汁).*")) return .65;
        // Category aliases help short Chinese questions without admitting all personal facts.
        String aliases = switch (row.memoryType() == null ? "" : row.memoryType()) {
            case "WORK" -> "工作 职业 职务";
            case "IDENTITY", "PROFILE" -> "姓名 年龄 身份";
            default -> "";
        };
        return MySqlMemoryRetriever.textScore(question, row.content(), aliases + " " + Objects.toString(row.summary(), ""));
    }
}
