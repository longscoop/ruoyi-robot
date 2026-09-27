package com.robot.platform.ai.memory.identity;

import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.member.binding.service.MemberRobotAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class ConversationIdentityResolver {

    private final MemberRobotAccessService accessService;
    private final double confidenceThreshold;

    public ConversationIdentityResolver(
            MemberRobotAccessService accessService,
            @Value("${robot.ai.memory.identity-confidence-threshold:0.85}") double confidenceThreshold) {
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("identity confidence threshold must be between 0 and 1");
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    public ConversationIdentity resolve(long tenantId, long robotId,
                                        RealtimeClientEvent.CandidateIdentity candidate) {
        if (candidate == null || candidate.type() == null
                || "ANONYMOUS".equalsIgnoreCase(candidate.type())
                || candidate.memberId() == null || candidate.memberId() <= 0
                || candidate.confidence() == null || candidate.confidence() < confidenceThreshold) {
            return ConversationIdentity.anonymous(tenantId, robotId);
        }

        String type = candidate.type().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("VOICEPRINT") && !type.equals("FACE") && !type.equals("APP_BOUND")) {
            return ConversationIdentity.anonymous(tenantId, robotId);
        }
        try {
            accessService.requireReadable(tenantId, candidate.memberId(), robotId);
        } catch (RuntimeException rejected) {
            return ConversationIdentity.anonymous(tenantId, robotId);
        }
        return new ConversationIdentity(tenantId, robotId, candidate.memberId(), type,
                candidate.confidence(), true);
    }
}
