package com.robot.platform.ai.memory.policy;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.extract.MemoryCandidate;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMemoryPolicyTest {
    private final DefaultMemoryPolicy policy = new DefaultMemoryPolicy(.75, .60);
    private final ConversationIdentity member = new ConversationIdentity(11L, 33L, 99L, "VOICEPRINT", .95, true);
    private final ConversationIdentity anonymous = ConversationIdentity.anonymous(11L, 33L);

    @Test void anonymousCannotWriteMemberMemory() {
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "PREFERENCE", "少糖", .9, .9, null), anonymous,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
    }

    @Test void lowConfidenceIsIgnored() {
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "PREFERENCE", "少糖", .9, .74, null), member,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
    }

    @Test void explicitRememberBypassesImportanceButNotAuthorization() {
        assertEquals(MemoryDecision.Action.CREATE, decide(candidate("MEMBER", "PREFERENCE", "咖啡少糖", .40, .95, null), member,
                MemoryDirectiveParser.Directive.REMEMBER, List.of()).action());
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "PREFERENCE", "咖啡少糖", .40, .95, null), anonymous,
                MemoryDirectiveParser.Directive.REMEMBER, List.of()).action());
    }

    @Test void duplicatePreferenceReplacesExistingActiveMemory() {
        AiMemoryDO existing = new AiMemoryDO();
        existing.setId(7L); existing.setScope("MEMBER"); existing.setMemoryType("PREFERENCE");
        existing.setContent("用户喝咖啡偏好少糖"); existing.setStatus("ACTIVE");
        MemoryDecision decision = decide(candidate("MEMBER", "PREFERENCE", "用户喝咖啡偏好少糖", .8, .95, null), member,
                MemoryDirectiveParser.Directive.NORMAL, List.of(existing));
        assertEquals(MemoryDecision.Action.REPLACE, decision.action());
        assertEquals(7L, decision.existingMemoryId());
    }

    @Test void temporaryEventRequiresFiniteExpiry() {
        MemoryCandidate temporary = candidate("MEMBER", "FACT", "今天下午三点开会", .9, .95, null);
        assertEquals(MemoryDecision.Action.IGNORE, decide(temporary, member,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
        temporary = candidate("MEMBER", "FACT", "今天下午三点开会", .9, .95,
                LocalDateTime.now().plusHours(6));
        assertEquals(MemoryDecision.Action.CREATE, decide(temporary, member,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
    }

    @Test void casualGreetingOrJokeIsIgnored() {
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "FACT", "你好", .9, .95, null), member,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "FACT", "讲个笑话", .9, .95, null), member,
                MemoryDirectiveParser.Directive.NORMAL, List.of()).action());
    }

    @Test void forgetDeletesOnlyWhenScopeIsAuthorized() {
        assertEquals(MemoryDecision.Action.DELETE_MATCHES, decide(candidate("MEMBER", "PREFERENCE", "咖啡", .1, .1, null), member,
                MemoryDirectiveParser.Directive.FORGET, List.of()).action());
        assertEquals(MemoryDecision.Action.IGNORE, decide(candidate("MEMBER", "PREFERENCE", "咖啡", .1, .1, null), anonymous,
                MemoryDirectiveParser.Directive.FORGET, List.of()).action());
    }

    private MemoryDecision decide(MemoryCandidate c, ConversationIdentity i,
                                  MemoryDirectiveParser.Directive d, List<AiMemoryDO> existing) {
        return policy.decide(c, i, d, existing);
    }

    private static MemoryCandidate candidate(String scope, String type, String content,
                                             double importance, double confidence, LocalDateTime expiresAt) {
        return new MemoryCandidate(scope, type, content, importance, confidence, expiresAt);
    }
}
