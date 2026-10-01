package com.robot.platform.ai.memory.provider;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class MemoryForgetTargets {
    private final MemoryExtractor extractor;
    public MemoryForgetTargets(MemoryExtractor e){extractor=e;}
    public List<String> extract(MemoryExtractor.CompletedTurn turn,ConversationIdentity i,AiAgentConfig a){
        if (a.conversationModelId()==null) throw new IllegalStateException("Forgetting by speech requires a CHAT model");
        return extractor.extract(turn,i,MemoryDirectiveParser.Directive.FORGET,a).stream()
                .filter(c -> "ROBOT".equalsIgnoreCase(c.scope()) || (i.memberMemoryAllowed() && i.memberId()!=null && java.util.Set.of("MEMBER","MEMBER_ROBOT").contains(c.scope().toUpperCase())))
                .map(c -> c.content()).filter(s -> s!=null&&!s.isBlank()).toList();
    }
    public static String normalize(String value){return value.replaceAll("[\\p{P}\\p{Z}\\s]","").toLowerCase(java.util.Locale.ROOT);}
}
