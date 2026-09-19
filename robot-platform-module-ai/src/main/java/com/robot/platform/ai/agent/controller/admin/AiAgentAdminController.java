package com.robot.platform.ai.agent.controller.admin;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/admin-api/ai/agents")
@Validated
@RequiredArgsConstructor
public class AiAgentAdminController {

    private final AiAgentService agentService;
    private final AiAgentRobotBindingService bindingService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:agent:query')")
    public CommonResult<List<AgentRespVO>> list() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(agentService.list(tenantId).stream().map(AiAgentAdminController::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:agent:query')")
    public CommonResult<AgentRespVO> get(@PathVariable long id) {
        return success(toResp(agentService.get(TenantContextHolder.getRequiredTenantId(), id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:agent:create')")
    public CommonResult<Long> create(@Valid @RequestBody AgentCreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        AiAgentDO agent = agentService.create(new AiAgentService.CreateAgentCommand(
                tenantId, request.getName(), request.getCode(), request.getDescription(), request.getSystemPromptId(),
                request.getConversationModelId(), request.getRealtimeModelId(), request.getAsrModelId(),
                request.getTtsModelId(), request.getRealtimeMode(), request.getMemoryMode(),
                request.isMemoryReadEnabled(), request.isMemoryWriteEnabled(), request.isKnowledgeEnabled(),
                request.getVoiceConfigJson(), request.getStatus()));
        return success(agent.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:agent:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody AgentUpdateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        agentService.update(new AiAgentService.UpdateAgentCommand(
                tenantId, id, request.getName(), request.getCode(), request.getDescription(), request.getSystemPromptId(),
                request.getConversationModelId(), request.getRealtimeModelId(), request.getAsrModelId(),
                request.getTtsModelId(), request.getRealtimeMode(), request.getMemoryMode(),
                request.isMemoryReadEnabled(), request.isMemoryWriteEnabled(), request.isKnowledgeEnabled(),
                request.getVoiceConfigJson(), request.getStatus()));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:agent:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        agentService.delete(TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

    @GetMapping("/{agentId}/robots")
    @PreAuthorize("@ss.hasPermission('ai:agent:query')")
    public CommonResult<List<AgentRobotRespVO>> listRobots(@PathVariable long agentId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(bindingService.list(tenantId, agentId).stream().map(AiAgentAdminController::toResp).toList());
    }

    @PostMapping("/{agentId}/robots")
    @PreAuthorize("@ss.hasPermission('ai:agent:bind')")
    public CommonResult<Long> bind(@PathVariable long agentId, @Valid @RequestBody AgentBindReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(bindingService.bind(tenantId, agentId, request.getRobotId(), request.isDefaultAgent()));
    }

    @DeleteMapping("/{agentId}/robots/{robotId}")
    @PreAuthorize("@ss.hasPermission('ai:agent:bind')")
    public CommonResult<Boolean> unbind(@PathVariable long agentId, @PathVariable long robotId) {
        bindingService.unbind(TenantContextHolder.getRequiredTenantId(), agentId, robotId);
        return success(true);
    }

    private static AgentRespVO toResp(AiAgentDO agent) {
        return new AgentRespVO(agent.getId(), agent.getName(), agent.getCode(), agent.getDescription(),
                agent.getSystemPromptId(), agent.getConversationModelId(), agent.getRealtimeModelId(),
                agent.getAsrModelId(), agent.getTtsModelId(), agent.getRealtimeMode(), agent.getMemoryMode(),
                Boolean.TRUE.equals(agent.getMemoryReadEnabled()), Boolean.TRUE.equals(agent.getMemoryWriteEnabled()),
                Boolean.TRUE.equals(agent.getKnowledgeEnabled()), agent.getVoiceConfigJson(), agent.getStatus());
    }

    private static AgentRobotRespVO toResp(AiAgentRobotDO binding) {
        return new AgentRobotRespVO(binding.getId(), binding.getAgentId(), binding.getRobotId(),
                Boolean.TRUE.equals(binding.getIsDefault()), binding.getStatus());
    }

    @Data
    public static class AgentCreateReqVO {
        private String name;
        private String code;
        private String description;
        private Long systemPromptId;
        private Long conversationModelId;
        private Long realtimeModelId;
        private Long asrModelId;
        private Long ttsModelId;
        private String realtimeMode;
        private String memoryMode;
        private boolean memoryReadEnabled;
        private boolean memoryWriteEnabled;
        private boolean knowledgeEnabled;
        private String voiceConfigJson;
        private String status;
    }

    @Data
    public static class AgentUpdateReqVO {
        private String name;
        private String code;
        private String description;
        private Long systemPromptId;
        private Long conversationModelId;
        private Long realtimeModelId;
        private Long asrModelId;
        private Long ttsModelId;
        private String realtimeMode;
        private String memoryMode;
        private boolean memoryReadEnabled;
        private boolean memoryWriteEnabled;
        private boolean knowledgeEnabled;
        private String voiceConfigJson;
        private String status;
    }

    @Data
    public static class AgentBindReqVO {
        private Long robotId;
        private boolean defaultAgent;
    }

    public record AgentRespVO(Long id, String name, String code, String description, Long systemPromptId,
                              Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                              String realtimeMode, String memoryMode, boolean memoryReadEnabled,
                              boolean memoryWriteEnabled, boolean knowledgeEnabled, String voiceConfigJson,
                              String status) {
    }

    public record AgentRobotRespVO(Long id, Long agentId, Long robotId, boolean defaultAgent, String status) {
    }
}
