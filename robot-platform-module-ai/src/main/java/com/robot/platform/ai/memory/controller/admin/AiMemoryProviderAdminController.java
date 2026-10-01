package com.robot.platform.ai.memory.controller.admin;

import com.robot.platform.ai.agent.service.*;
import com.robot.platform.ai.memory.provider.*;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.MemorySnippet;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/ai/memory-providers")
public class AiMemoryProviderAdminController {
    private final MemoryProviderRegistry providers;
    private final AiAgentService agents;
    private final AiAgentRobotBindingService bindings;
    public AiMemoryProviderAdminController(MemoryProviderRegistry p,AiAgentService a,AiAgentRobotBindingService b){providers=p;agents=a;bindings=b;}
    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:memory:query') or @ss.hasPermission('ai:agent:query')")
    public CommonResult<List<ProviderStatus>> list(){
        long tenant=TenantContextHolder.getRequiredTenantId();
        return success(List.of(
                status("NOMEM","关闭记忆",tenant),status("MEM_LOCAL_SHORT","本地短期记忆",tenant),
                status("MEM0AI","Mem0 记忆服务",tenant),status("POWERMEM","PowerMem 智能记忆",tenant),
                status("LONG_TERM","数据库分类记忆（兼容）",tenant)));
    }
    private ProviderStatus status(String mode,String label,long tenant){
        return new ProviderStatus(mode,MemoryModes.provider(mode),label,providers.require(mode).configured(tenant),providers.saveMessageThreshold());
    }
    @GetMapping("/{agentId}/query")
    @PreAuthorize("@ss.hasPermission('ai:memory:query')")
    public CommonResult<List<MemorySnippet>> query(@PathVariable long agentId,@RequestParam long robotId,
                                                  @RequestParam(defaultValue="") String question){
        long tenant=TenantContextHolder.getRequiredTenantId();
        var agent=agents.getResolvedConfig(tenant,agentId);
        bindings.requireAgentForRobot(tenant,robotId,agent.code());
        if(question.length()>2000)throw new IllegalArgumentException("Memory question too long");
        // Admin robot preview cannot assert a biometric/member identity.
        return success(providers.queryMemory(ConversationIdentity.anonymous(tenant,robotId),agent,question,20));
    }
    public record ProviderStatus(String mode,String provider,String name,boolean configured,int saveMessageThreshold){}
}
