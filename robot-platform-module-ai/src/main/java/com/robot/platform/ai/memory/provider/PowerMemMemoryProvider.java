package com.robot.platform.ai.memory.provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.memory.service.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class PowerMemMemoryProvider implements MemoryProvider {
    private final MemoryProviderProperties properties;
    private final RemoteMemoryHttp http;
    private final MemoryDirectiveParser parser;
    private final MemoryForgetTargets forgetting;
    public PowerMemMemoryProvider(MemoryProviderProperties p,RemoteMemoryHttp h,MemoryDirectiveParser parser,MemoryForgetTargets forgetting){properties=p;http=h;this.parser=parser;this.forgetting=forgetting;}
    public String name(){return "powermem";}
    public boolean configured(long tenant){var c=properties.remote(name(),tenant);return c.isEnabled()&&!c.getApiKey().isBlank();}
    private MemoryProviderProperties.Remote config(long tenant){if(!configured(tenant))throw new IllegalStateException("PowerMem not configured");return properties.remote(name(),tenant);}
    public List<MemorySnippet> queryMemory(ConversationIdentity i,AiAgentConfig a,String question,int limit){
        var c=config(i.tenantId());String ns=MemoryNamespace.of(i,a);
        JsonNode root=http.request(c,"POST","/query",Map.of("namespace",ns,"query",question==null||question.isBlank()?"用户身份与沟通偏好":question,"limit",limit),c.getQueryTimeoutMillis(),"Bearer");
        List<MemorySnippet> out=new ArrayList<>();
        // Whole profiles mix unrelated facts; inject only individually retrieved facts.
        JsonNode rows=root.path("results");
        if(rows.isArray())for(JsonNode row:rows){
            if(row.hasNonNull("user_id")&&!ns.equals(row.path("user_id").asText()))continue;
            String text=row.path("memory").asText(row.path("content").asText(""));
            String type=row.path("metadata").path("memory_type").asText("FACT");
            try{type=MemoryCategories.validate(type);}catch(IllegalArgumentException ignored){type="FACT";}
            if(!text.isBlank())out.add(new MemorySnippet(0,"ROBOT",type,text,null,row.path("score").asDouble(0)));
        }
        return out.stream().limit(limit).toList();
    }
    public void saveMemory(ConversationIdentity i,AiAgentConfig a,List<CompletedTurn> messages){
        var c=config(i.tenantId());String ns=MemoryNamespace.of(i,a);
        List<Map<String,String>> evidence=new ArrayList<>();
        for(var turn:messages){var d=parser.parse(turn.userText());
            if(d==MemoryDirectiveParser.Directive.FORGET) for(String target:forgetting.extract(turn,i,a))
                http.request(c,"POST","/forget",Map.of("namespace",ns,"content",target),c.getSaveTimeoutMillis(),"Bearer");
            if(d!=MemoryDirectiveParser.Directive.DO_NOT_REMEMBER&&d!=MemoryDirectiveParser.Directive.FORGET)evidence.add(Map.of("role","user","content",turn.userText()));}
        if(!evidence.isEmpty())http.request(c,"POST","/save",Map.of("namespace",ns,"messages",evidence),c.getSaveTimeoutMillis(),"Bearer");
    }
}
