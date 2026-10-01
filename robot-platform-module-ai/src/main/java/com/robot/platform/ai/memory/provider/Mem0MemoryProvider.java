package com.robot.platform.ai.memory.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.memory.service.MemorySnippet;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Mem0MemoryProvider implements MemoryProvider {
    private final MemoryProviderProperties properties;
    private final RemoteMemoryHttp http;
    private final MemoryDirectiveParser parser;
    private final MemoryForgetTargets forgetting;
    public Mem0MemoryProvider(MemoryProviderProperties p,RemoteMemoryHttp h,MemoryDirectiveParser parser, MemoryForgetTargets forgetting){properties=p;http=h;this.parser=parser;this.forgetting=forgetting;}
    public String name(){return "mem0ai";}
    public boolean configured(long tenant){var c=properties.remote(name(),tenant);return c.isEnabled()&&!c.getApiKey().isBlank();}
    private MemoryProviderProperties.Remote config(long tenant){if(!configured(tenant))throw new IllegalStateException("Mem0 not configured");return properties.remote(name(),tenant);}
    public List<MemorySnippet> queryMemory(ConversationIdentity i,AiAgentConfig a,String question,int limit){
        var c=config(i.tenantId());
        String ns=MemoryNamespace.of(i,a);
        JsonNode root=http.request(c,"POST",c.getSearchPath(),Map.of("query",question==null||question.isBlank()?"用户身份、工作与沟通偏好":question,
                "filters",Map.of("user_id",ns),"top_k",limit),c.getQueryTimeoutMillis(),"Token");
        JsonNode rows=root.isArray()?root:root.path("results");
        List<MemorySnippet> out=new ArrayList<>();
        if(rows.isArray())for(JsonNode row:rows){
            if(row.hasNonNull("user_id")&&!ns.equals(row.path("user_id").asText()))continue;
            String content=row.path("memory").asText("");
            if(!content.isBlank())out.add(new MemorySnippet(0,"ROBOT","FACT",content,null,row.path("score").asDouble(0)));
        }
        return out.stream().limit(limit).toList();
    }
    public void saveMemory(ConversationIdentity i,AiAgentConfig a,List<CompletedTurn> messages){
        var c=config(i.tenantId());String ns=MemoryNamespace.of(i,a);
        List<Map<String,String>> evidence=new ArrayList<>();
        for(var turn:messages){
            var directive=parser.parse(turn.userText());
            if(directive==MemoryDirectiveParser.Directive.FORGET) {
                for(String target:forgetting.extract(turn,i,a)) {
                    JsonNode found=http.request(c,"POST",c.getSearchPath(),Map.of("query",target,"filters",Map.of("user_id",ns),"top_k",100),c.getSaveTimeoutMillis(),"Token");
                    JsonNode rows=found.isArray()?found:found.path("results");
                    if(rows.isArray())for(JsonNode row:rows) {
                        String id=row.path("id").asText("");
                        if(row.hasNonNull("user_id")&&!ns.equals(row.path("user_id").asText()))continue;
                        if(id.matches("[a-zA-Z0-9_-]+")&&MemoryForgetTargets.normalize(target).equals(MemoryForgetTargets.normalize(row.path("memory").asText(""))))
                            http.request(c,"DELETE","/v1/memories/"+id+"/",null,c.getSaveTimeoutMillis(),"Token");
                    }
                }
            }
            if(directive==MemoryDirectiveParser.Directive.DO_NOT_REMEMBER||directive==MemoryDirectiveParser.Directive.FORGET)continue;
            evidence.add(Map.of("role","user","content",turn.userText()));
        }
        if(!evidence.isEmpty())http.request(c,"POST",c.getAddPath(),Map.of("messages",evidence,"user_id",ns,
                "metadata",Map.of("tenant_id",i.tenantId(),"agent_id",a.agentId(),"robot_id",i.robotId())),c.getSaveTimeoutMillis(),"Token");
    }
}
