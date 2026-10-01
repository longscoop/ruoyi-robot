package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.memory.extract.MemoryCandidate;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryPolicy;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.memory.service.MemoryStore;
import com.robot.platform.ai.memory.service.MemoryWrite;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class MemoryCandidateWriter {
    private final AiMemoryMapper mapper;
    private final MemoryPolicy policy;
    private final MemoryStore store;

    public MemoryCandidateWriter(AiMemoryMapper mapper, MemoryPolicy policy, MemoryStore store) {
        this.mapper = mapper;
        this.policy = policy;
        this.store = store;
    }

    @Transactional
    public void write(List<MemoryCandidate> candidates, ConversationIdentity identity,
                      MemoryDirectiveParser.Directive directive, MemoryExtractor.CompletedTurn turn) {
        if (directive == MemoryDirectiveParser.Directive.DO_NOT_REMEMBER) return;
        for (MemoryCandidate candidate : candidates) {
            if (candidate == null) continue;
            Long member = identity.memberMemoryAllowed() ? identity.memberId() : null;
            var active = mapper.selectActiveCandidates(identity.tenantId(), identity.robotId(), member, LocalDateTime.now());
            // Duplicate detection and deletion must stay inside the exact target scope.
            var scoped = active.stream().filter(m -> candidate.scope().equalsIgnoreCase(m.getScope())).toList();
            var decision = policy.decide(candidate, identity, directive, scoped);
            switch (decision.action()) {
                case IGNORE -> { }
                case DELETE_MATCHES -> scoped.stream()
                        .filter(m -> normalize(m.getContent()).equals(normalize(candidate.content())))
                        .forEach(m -> store.delete(identity.tenantId(), m.getId()));
                case CREATE, REPLACE -> {
                    String scope = candidate.scope().toUpperCase(Locale.ROOT);
                    long id = store.save(new MemoryWrite(identity.tenantId(), scope,
                            scope.equals("ROBOT") ? null : member,
                            scope.equals("MEMBER") ? null : identity.robotId(),
                            candidate.memoryType(), candidate.content(), null,
                            BigDecimal.valueOf(candidate.importance()), BigDecimal.valueOf(candidate.confidence()),
                            turn.conversationId(), null, null, null, candidate.expiresAt()));
                    if (decision.existingMemoryId() != null) store.supersede(identity.tenantId(), decision.existingMemoryId(), id);
                }
            }
        }
    }

    private static String normalize(String text) {
        return Objects.toString(text, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
