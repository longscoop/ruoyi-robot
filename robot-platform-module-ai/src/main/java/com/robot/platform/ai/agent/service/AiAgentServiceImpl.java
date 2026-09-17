package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.robot.platform.ai.enums.AiErrorCodeConstants.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {
    private static final Set<String> MEMORY_MODES = Set.of("NONE", "SESSION", "LONG_TERM");

    private final AiAgentMapper mapper;
    private final AiPromptService promptService;
    private final AiModelService modelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long create(CreateAgentCommand command) {
        requireCurrentTenant(command.tenantId());
        validateReferences(command.tenantId(), command.systemPromptId(), command.realtimeMode(),
                command.conversationModelId(), command.realtimeModelId(), command.asrModelId(), command.ttsModelId(),
                command.memoryMode());
        AiAgentDO row = AiAgentDO.builder()
                .tenantId(command.tenantId())
                .name(command.name())
                .code(command.code())
                .description(command.description())
                .systemPromptId(command.systemPromptId())
                .conversationModelId(command.conversationModelId())
                .realtimeModelId(command.realtimeModelId())
                .asrModelId(command.asrModelId())
                .ttsModelId(command.ttsModelId())
                .realtimeMode(command.realtimeMode())
                .memoryMode(command.memoryMode())
                .memoryReadEnabled(command.memoryReadEnabled())
                .memoryWriteEnabled(command.memoryWriteEnabled())
                .knowledgeEnabled(command.knowledgeEnabled())
                .voiceConfigJson(command.voiceConfigJson())
                .status(defaultStatus(command.status()))
                .build();
        mapper.insert(row);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateAgentCommand command) {
        requireCurrentTenant(command.tenantId());
        AiAgentDO row = requireAgent(command.tenantId(), command.id());
        validateReferences(command.tenantId(), command.systemPromptId(), command.realtimeMode(),
                command.conversationModelId(), command.realtimeModelId(), command.asrModelId(), command.ttsModelId(),
                command.memoryMode());
        row.setName(command.name());
        row.setCode(command.code());
        row.setDescription(command.description());
        row.setSystemPromptId(command.systemPromptId());
        row.setConversationModelId(command.conversationModelId());
        row.setRealtimeModelId(command.realtimeModelId());
        row.setAsrModelId(command.asrModelId());
        row.setTtsModelId(command.ttsModelId());
        row.setRealtimeMode(command.realtimeMode());
        row.setMemoryMode(command.memoryMode());
        row.setMemoryReadEnabled(command.memoryReadEnabled());
        row.setMemoryWriteEnabled(command.memoryWriteEnabled());
        row.setKnowledgeEnabled(command.knowledgeEnabled());
        row.setVoiceConfigJson(command.voiceConfigJson());
        row.setStatus(defaultStatus(command.status()));
        mapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        AiAgentDO row = requireAgent(tenantId, id);
        mapper.deleteById(row.getId());
    }

    @Override
    public AiAgentDO get(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        return requireAgent(tenantId, id);
    }

    @Override
    public List<AiAgentDO> list(long tenantId) {
        requireCurrentTenant(tenantId);
        return mapper.selectByTenantId(tenantId);
    }

    @Override
    public AiAgentConfig getResolvedConfig(long tenantId, long agentId) {
        requireCurrentTenant(tenantId);
        AiAgentDO agent = requireAgent(tenantId, agentId);
        AiPromptDO prompt = validateReferences(tenantId, agent.getSystemPromptId(), agent.getRealtimeMode(),
                agent.getConversationModelId(), agent.getRealtimeModelId(), agent.getAsrModelId(), agent.getTtsModelId(),
                agent.getMemoryMode());
        return new AiAgentConfig(agent.getId(), agent.getTenantId(), agent.getCode(), prompt.getContent(), prompt.getId(),
                prompt.getVersion(), agent.getRealtimeMode(), agent.getConversationModelId(), agent.getRealtimeModelId(),
                agent.getAsrModelId(), agent.getTtsModelId(), agent.getMemoryMode(),
                Boolean.TRUE.equals(agent.getMemoryReadEnabled()), Boolean.TRUE.equals(agent.getMemoryWriteEnabled()));
    }

    private AiPromptDO validateReferences(long tenantId, long systemPromptId, String realtimeMode,
                                          Long conversationModelId, Long realtimeModelId, Long asrModelId, Long ttsModelId,
                                          String memoryMode) {
        AiPromptDO prompt = promptService.get(tenantId, systemPromptId);
        if (!"SYSTEM".equals(prompt.getType()) || !MEMORY_MODES.contains(memoryMode)) {
            throw exception(AI_AGENT_CONFIG_INVALID);
        }
        switch (realtimeMode) {
            case "NATIVE" -> requireNative(tenantId, realtimeModelId);
            case "CASCADE" -> requireCascade(tenantId, conversationModelId, asrModelId, ttsModelId);
            case "AUTO" -> {
                requireNative(tenantId, realtimeModelId);
                requireCascade(tenantId, conversationModelId, asrModelId, ttsModelId);
            }
            default -> throw exception(AI_AGENT_CONFIG_INVALID);
        }
        return prompt;
    }

    private void requireNative(long tenantId, Long realtimeModelId) {
        if (realtimeModelId == null) {
            throw exception(AI_AGENT_CONFIG_INVALID);
        }
        modelService.requireType(tenantId, realtimeModelId, "REALTIME_S2S");
    }

    private void requireCascade(long tenantId, Long conversationModelId, Long asrModelId, Long ttsModelId) {
        if (conversationModelId == null || asrModelId == null || ttsModelId == null) {
            throw exception(AI_AGENT_CONFIG_INVALID);
        }
        modelService.requireType(tenantId, conversationModelId, "CHAT");
        modelService.requireType(tenantId, asrModelId, "ASR");
        modelService.requireType(tenantId, ttsModelId, "TTS");
    }

    private AiAgentDO requireAgent(long tenantId, long id) {
        AiAgentDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw exception(AI_AGENT_NOT_EXISTS);
        }
        return row;
    }

    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) {
            throw exception(AI_TENANT_FORBIDDEN);
        }
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status;
    }
}
