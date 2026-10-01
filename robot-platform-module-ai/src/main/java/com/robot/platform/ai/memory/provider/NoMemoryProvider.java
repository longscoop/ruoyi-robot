package com.robot.platform.ai.memory.provider;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.MemorySnippet;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class NoMemoryProvider implements MemoryProvider {
    public String name() { return "nomem"; }
    public List<MemorySnippet> queryMemory(ConversationIdentity i, AiAgentConfig a, String q, int limit) { return List.of(); }
    public void saveMemory(ConversationIdentity i, AiAgentConfig a, List<CompletedTurn> messages) { }
}
