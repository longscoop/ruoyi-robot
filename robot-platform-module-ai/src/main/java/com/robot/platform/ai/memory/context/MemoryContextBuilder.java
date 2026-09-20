package com.robot.platform.ai.memory.context;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.MemoryQuery;
import com.robot.platform.ai.memory.service.MemoryRetriever;
import com.robot.platform.ai.memory.service.MemorySnippet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class MemoryContextBuilder {
    private final MemoryRetriever retriever;
    private final int topK;

    public MemoryContextBuilder(MemoryRetriever retriever,
            @Value("${robot.ai.memory.recall-top-k:8}") int topK) {
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        if (topK < 1 || topK > 100) throw new IllegalArgumentException("recallTopK must be between 1 and 100");
        this.topK = topK;
    }

    public String buildContext(ConversationIdentity identity, AiAgentConfig agent, String currentUserText) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(agent, "agent");
        if (!agent.memoryReadEnabled()) return "";
        Long memberId = identity.memberMemoryAllowed() ? identity.memberId() : null;
        List<MemorySnippet> memories = retriever.retrieve(
                new MemoryQuery(identity.tenantId(), identity.robotId(), memberId, currentUserText, null, LocalDateTime.now()), topK);
        if (memories.isEmpty()) return "";
        StringBuilder out = new StringBuilder("<retrieved_memory>\n");
        memories.stream().limit(topK).forEach(m -> out.append("- ").append(m.content()).append("\n"));
        return out.append("</retrieved_memory>").toString();
    }
}
