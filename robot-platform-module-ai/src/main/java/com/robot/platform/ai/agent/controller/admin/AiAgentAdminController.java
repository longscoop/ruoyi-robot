package com.robot.platform.ai.agent.controller.admin;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.ai.agent.service.*;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    public CommonResult<Long> create(@Valid @RequestBody AgentReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(agentService.create(toCreateCommand(tenantId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('ai:agent:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody AgentReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        agentService.update(toUpdateCommand(tenantId, id, request));
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
    public CommonResult<List<AgentRobotRespVO>> listRobots(@PathVariable long agentId, @RequestParam long robotId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        agentService.get(tenantId, agentId);
        return success(bindingService.listByRobot(tenantId, robotId).stream()
                .filter(row -> row.getAgentId().equals(agentId))
                .map(AiAgentAdminController::toBindingResp).toList());
    }

    @PostMapping("/{agentId}/robots")
    @PreAuthorize("@ss.hasPermission('ai:agent:bind')")
    public CommonResult<Long> bindRobot(@PathVariable long agentId, @Valid @RequestBody AgentRobotBindReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return success(bindingService.bind(tenantId, agentId, request.getRobotId(), request.isDefaultAgent()));
    }

    @PutMapping("/{agentId}/robots/{robotId}/default")
    @PreAuthorize("@ss.hasPermission('ai:agent:bind')")
    public CommonResult<Boolean> setDefault(@PathVariable long agentId, @PathVariable long robotId) {
        bindingService.setDefault(TenantContextHolder.getRequiredTenantId(), agentId, robotId);
        return success(true);
    }

    @DeleteMapping("/{agentId}/robots/{robotId}")
    @PreAuthorize("@ss.hasPermission('ai:agent:bind')")
    public CommonResult<Boolean> unbindRobot(@PathVariable long agentId, @PathVariable long robotId) {
        bindingService.unbind(TenantContextHolder.getRequiredTenantId(), agentId, robotId);
        return success(true);
    }

    private static CreateAgentCommand toCreateCommand(long tenantId, AgentReqVO request) {
        return new CreateAgentCommand(tenantId, request.getName(), request.getCode(), request.getDescription(),
                request.getSystemPromptId(), request.getConversationModelId(), request.getRealtimeModelId(),
                request.getAsrModelId(), request.getTtsModelId(), request.getRealtimeMode(), request.getMemoryMode(),
                request.isMemoryReadEnabled(), request.isMemoryWriteEnabled(), request.isKnowledgeEnabled(),
                request.getVoiceConfigJson(), request.getStatus());
    }

    private static UpdateAgentCommand toUpdateCommand(long tenantId, long id, AgentReqVO request) {
        return new UpdateAgentCommand(tenantId, id, request.getName(), request.getCode(), request.getDescription(),
                request.getSystemPromptId(), request.getConversationModelId(), request.getRealtimeModelId(),
                request.getAsrModelId(), request.getTtsModelId(), request.getRealtimeMode(), request.getMemoryMode(),
                request.isMemoryReadEnabled(), request.isMemoryWriteEnabled(), request.isKnowledgeEnabled(),
                request.getVoiceConfigJson(), request.getStatus());
    }

    private static AgentRespVO toResp(AiAgentDO row) {
        return new AgentRespVO(row.getId(), row.getName(), row.getCode(), row.getDescription(), row.getSystemPromptId(),
                row.getConversationModelId(), row.getRealtimeModelId(), row.getAsrModelId(), row.getTtsModelId(),
                row.getRealtimeMode(), row.getMemoryMode(), Boolean.TRUE.equals(row.getMemoryReadEnabled()),
                Boolean.TRUE.equals(row.getMemoryWriteEnabled()), Boolean.TRUE.equals(row.getKnowledgeEnabled()),
                row.getVoiceConfigJson(), row.getStatus());
    }

    private static AgentRobotRespVO toBindingResp(AiAgentRobotDO row) {
        return new AgentRobotRespVO(row.getId(), row.getAgentId(), row.getRobotId(), Boolean.TRUE.equals(row.getIsDefault()),
                row.getStatus());
    }

    @Data
    public static class AgentReqVO {
        @NotBlank @Size(max = 128) private String name;
        @NotBlank @Size(max = 64) private String code;
        private String description;
        @NotNull private Long systemPromptId;
        private Long conversationModelId;
        private Long realtimeModelId;
        private Long asrModelId;
        private Long ttsModelId;
        @NotBlank private String realtimeMode;
        @NotBlank private String memoryMode = "SESSION";
        private boolean memoryReadEnabled = true;
        private boolean memoryWriteEnabled = true;
        private boolean knowledgeEnabled;
        private String voiceConfigJson;
        private String status = "ENABLED";
    }

    @Data
    public static class AgentRobotBindReqVO {
        @NotNull private Long robotId;
        private boolean defaultAgent;
    }

    public record AgentRespVO(long id, String name, String code, String description, long systemPromptId,
                              Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                              String realtimeMode, String memoryMode, boolean memoryReadEnabled,
                              boolean memoryWriteEnabled, boolean knowledgeEnabled, String voiceConfigJson,
                              String status) { }

    public record AgentRobotRespVO(long id, long agentId, long robotId, boolean defaultAgent, String status) { }
}
