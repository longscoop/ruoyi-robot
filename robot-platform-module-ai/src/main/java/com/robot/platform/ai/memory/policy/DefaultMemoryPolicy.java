package com.robot.platform.ai.memory.policy;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.extract.MemoryCandidate;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class DefaultMemoryPolicy implements MemoryPolicy {
    private final double minConfidence;
    private final double minImportance;

    public DefaultMemoryPolicy(
            @Value("${robot.ai.memory.min-confidence:0.75}") double minConfidence,
            @Value("${robot.ai.memory.min-importance:0.60}") double minImportance) {
        this.minConfidence = probability(minConfidence, "minConfidence");
        this.minImportance = probability(minImportance, "minImportance");
    }

    @Override
    public MemoryDecision decide(MemoryCandidate candidate, ConversationIdentity identity,
                                 MemoryDirectiveParser.Directive directive, List<AiMemoryDO> activeMemories) {
        Objects.requireNonNull(identity, "identity");
        directive = directive == null ? MemoryDirectiveParser.Directive.NORMAL : directive;
        if (directive == MemoryDirectiveParser.Directive.DO_NOT_REMEMBER) {
            return MemoryDecision.ignore("explicit do-not-remember");
        }
        if (directive == MemoryDirectiveParser.Directive.FORGET) {
            return authorizedForForget(candidate, identity)
                    ? MemoryDecision.deleteMatches("explicit forget within authorized scope")
                    : MemoryDecision.ignore("forget scope not authorized");
        }
        if (candidate == null) return MemoryDecision.ignore("no candidate");
        if (!authorized(candidate, identity)) return MemoryDecision.ignore("candidate scope not authorized");
        if (candidate.confidence() < minConfidence) return MemoryDecision.ignore("confidence below threshold");
        if (requiresExpiry(candidate) && candidate.expiresAt() == null) {
            return MemoryDecision.ignore("temporary memory requires expiresAt");
        }
        if (isCasual(candidate)) return MemoryDecision.ignore("casual content is not long-term memory");
        if (directive != MemoryDirectiveParser.Directive.REMEMBER && candidate.importance() < minImportance) {
            return MemoryDecision.ignore("importance below threshold");
        }
        AiMemoryDO duplicate = findDuplicate(candidate, activeMemories);
        if (duplicate != null) return MemoryDecision.replace(duplicate.getId(), "duplicate memory refresh");
        return MemoryDecision.create(directive == MemoryDirectiveParser.Directive.REMEMBER
                ? "explicit remember" : "retention thresholds satisfied");
    }

    private static boolean authorized(MemoryCandidate candidate, ConversationIdentity identity) {
        String scope = normalize(candidate.scope());
        return switch (scope) {
            case "MEMBER" -> identity.memberMemoryAllowed() && identity.memberId() != null;
            case "MEMBER_ROBOT" -> identity.memberMemoryAllowed() && identity.memberId() != null && identity.robotId() > 0;
            case "ROBOT" -> identity.robotId() > 0;
            default -> false;
        };
    }

    private static boolean authorizedForForget(MemoryCandidate candidate, ConversationIdentity identity) {
        return candidate != null && authorized(candidate, identity);
    }

    private static AiMemoryDO findDuplicate(MemoryCandidate candidate, List<AiMemoryDO> active) {
        if (active == null) return null;
        String normalized = normalizeText(candidate.content());
        for (AiMemoryDO row : active) {
            if (row == null || row.getId() == null || !"ACTIVE".equalsIgnoreCase(row.getStatus())) continue;
            if (!normalize(candidate.scope()).equals(normalize(row.getScope()))) continue;
            if (!normalize(candidate.memoryType()).equals(normalize(row.getMemoryType()))) continue;
            String existing = normalizeText(row.getContent());
            if (existing.equals(normalized) || containsNearDuplicate(existing, normalized)) return row;
        }
        return null;
    }

    private static boolean containsNearDuplicate(String a, String b) {
        if (a.length() < 4 || b.length() < 4) return false;
        return a.contains(b) || b.contains(a);
    }

    private static boolean requiresExpiry(MemoryCandidate candidate) {
        String type = normalize(candidate.memoryType());
        String text = candidate.content() == null ? "" : candidate.content();
        return type.equals("EVENT") || text.contains("今天") || text.contains("明天")
                || text.contains("下午") || text.contains("上午") || text.contains("今晚")
                || text.toLowerCase(Locale.ROOT).contains("today")
                || text.toLowerCase(Locale.ROOT).contains("tomorrow");
    }

    private static boolean isCasual(MemoryCandidate candidate) {
        String text = normalizeText(candidate.content());
        return text.equals("你好") || text.equals("您好") || text.equals("hello") || text.equals("hi")
                || text.contains("讲个笑话") || text.contains("tell me a joke");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static double probability(double value, String label) {
        if (value < 0 || value > 1) throw new IllegalArgumentException(label + " must be between 0 and 1");
        return value;
    }
}
