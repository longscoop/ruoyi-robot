package com.robot.platform.ai.memory.provider;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.pipeline.MemoryCandidateWriter;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.memory.service.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
@Component
public class MySqlMemoryProvider implements MemoryProvider {
    private final MemoryRetriever retriever;
    private final MemoryExtractor extractor;
    private final MemoryCandidateWriter writer;
    private final MemoryDirectiveParser parser;
    public MySqlMemoryProvider(MemoryRetriever retriever, MemoryExtractor extractor, MemoryCandidateWriter writer, MemoryDirectiveParser parser) {
        this.retriever = retriever; this.extractor = extractor; this.writer = writer; this.parser = parser;
    }
    public String name() { return "mysql"; }
    public List<MemorySnippet> queryMemory(ConversationIdentity i, AiAgentConfig a, String q, int limit) {
        MemoryNamespace.of(i,a);
        MemoryQuery query = new MemoryQuery(i.tenantId(), i.robotId(), i.memberMemoryAllowed() ? i.memberId() : null, q, null, LocalDateTime.now());
        return q == null || q.isBlank() ? retriever.retrieveBackground(query,limit) : retriever.retrieve(query,limit);
    }
    public void saveMemory(ConversationIdentity i, AiAgentConfig a, List<CompletedTurn> messages) {
        MemoryNamespace.of(i,a);
        for (CompletedTurn turn : messages) {
            var directive = parser.parse(turn.userText());
            if (directive != MemoryDirectiveParser.Directive.DO_NOT_REMEMBER)
                writer.write(extractor.extract(turn,i,directive,a), i,directive,turn);
        }
    }
}
