package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiAgentServiceImpl implements AiAgentService {
    private static final Set<String> REALTIME_MODES = Set.of("NATIVE", "CASCADE", "AUTO");
    private static final Set<String> MEMORY_MODES = Set.of("NONE", "SESSION", "LONG_TERM");
    private static final String DEFAULT_STATUS = "ENABLED";

    private final AiAgentMapper agentMapper;
    private final AiPromptMapper promptMapper;
    private final AiModelMapper modelMapper;

    public AiAgentServiceImpl(AiAgentMapper agentMapper, AiPromptMapper promptMapper, AiModelMapper modelMapper) {
        this.agentMapper = agentMapper;
        this.promptMapper = promptMapper;
        this.modelMapper = modelMapper;
    }

    @Override
    public AiAgentDO create(CreateAgentCommand command) {
        requireTenant(command.tenantId());
        ValidatedAgentConfig config = validate(command.tenantId(), command.name(), command.code(),
                command.systemPromptId(), command.conversationModelId(), command.realtimeModelId(),
                command.asrModelId(), command.ttsModelId(), command.realtimeMode(), command.memoryMode());

        AiAgentDO row = new AiAgentDO();
        apply(row, command.tenantId(), command.name(), command.code(), command.description(),
                command.systemPromptId(), command.conversationModelId(), command.realtimeModelId(),
                command.asrModelId(), command.ttsModelId(), config.realtimeMode(), config.memoryMode(),
                command.memoryReadEnabled(), command.memoryWriteEnabled(), command.knowledgeEnabled(),
                command.voiceConfigJson(), command.status());
        try {
            agentMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Agent code already exists in current tenant");
        }
        return row;
    }

    @Override
    public AiAgentDO update(UpdateAgentCommand command) {
        requireTenant(command.tenantId());
        AiAgentDO row = requireAgent(command.tenantId(), command.id());
        ValidatedAgentConfig config = validate(command.tenantId(), command.name(), command.code(),
                command.systemPromptId(), command.conversationModelId(), command.realtimeModelId(),
                command.asrModelId(), command.ttsModelId(), command.realtimeMode(), command.memoryMode());

        apply(row, command.tenantId(), command.name(), command.code(), command.description(),
                command.systemPromptId(), command.conversationModelId(), command.realtimeModelId(),
                command.asrModelId(), command.ttsModelId(), config.realtimeMode(), config.memoryMode(),
                command.memoryReadEnabled(), command.memoryWriteEnabled(), command.knowledgeEnabled(),
                command.voiceConfigJson(), command.status());
        try {
            agentMapper.updateById(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Agent code already exists in current tenant");
        }
        return row;
    }

    @Override
    public AiAgentDO get(long tenantId, long id) {
        requireTenant(tenantId);
        return requireAgent(tenantId, id);
    }

    @Override
    public List<AiAgentDO> list(long tenantId) {
        requireTenant(tenantId);
        return agentMapper.selectByTenantId(tenantId);
    }

    @Override
    public void delete(long tenantId, long id) {
        requireTenant(tenantId);
        requireAgent(tenantId, id);
        agentMapper.logicalDeleteByIdAndTenantId(id, tenantId);
    }

    @Override
    public AiAgentConfig getResolvedConfig(long tenantId, long agentId) {
        requireTenant(tenantId);
        AiAgentDO agent = requireAgent(tenantId, agentId);
        AiPromptDO prompt = requireSystemPrompt(tenantId, agent.getSystemPromptId());
        validateModelRoute(tenantId, agent.getRealtimeMode(), agent.getConversationModelId(),
                agent.getRealtimeModelId(), agent.getAsrModelId(), agent.getTtsModelId());

        return new AiAgentConfig(agent.getId(), agent.getTenantId(), agent.getCode(), prompt.getContent(),
                prompt.getId(), prompt.getVersion(), agent.getRealtimeMode(), agent.getConversationModelId(),
                agent.getRealtimeModelId(), agent.getAsrModelId(), agent.getTtsModelId(), agent.getVoiceConfigJson(),
                agent.getMemoryMode(), Boolean.TRUE.equals(agent.getMemoryReadEnabled()),
                Boolean.TRUE.equals(agent.getMemoryWriteEnabled()));
    }

    private ValidatedAgentConfig validate(long tenantId, String name, String code, long promptId,
                                          Long conversationModelId, Long realtimeModelId,
                                          Long asrModelId, Long ttsModelId,
                                          String realtimeMode, String memoryMode) {
        requireText(name, "Agent name must not be blank");
        requireText(code, "Agent code must not be blank");
        requireSystemPrompt(tenantId, promptId);
        String normalizedRealtimeMode = normalize(realtimeMode, REALTIME_MODES, "realtime mode");
        String normalizedMemoryMode = normalize(memoryMode, MEMORY_MODES, "memory mode");
        validateModelRoute(tenantId, normalizedRealtimeMode, conversationModelId, realtimeModelId, asrModelId, ttsModelId);
        return new ValidatedAgentConfig(normalizedRealtimeMode, normalizedMemoryMode);
    }

    private void validateModelRoute(long tenantId, String mode, Long conversationModelId,
                                    Long realtimeModelId, Long asrModelId, Long ttsModelId) {
        switch (mode) {
            case "NATIVE" -> {
                requireModelType(tenantId, realtimeModelId, "REALTIME_S2S", "realtimeModelId");
                validateOptionalModel(tenantId, conversationModelId, "CHAT", "conversationModelId");
                validateOptionalModel(tenantId, asrModelId, "ASR", "asrModelId");
                validateOptionalModel(tenantId, ttsModelId, "TTS", "ttsModelId");
            }
            case "CASCADE" -> {
                requireModelType(tenantId, conversationModelId, "CHAT", "conversationModelId");
                requireModelType(tenantId, asrModelId, "ASR", "asrModelId");
                requireModelType(tenantId, ttsModelId, "TTS", "ttsModelId");
                validateOptionalModel(tenantId, realtimeModelId, "REALTIME_S2S", "realtimeModelId");
            }
            case "AUTO" -> {
                requireModelType(tenantId, realtimeModelId, "REALTIME_S2S", "realtimeModelId");
                requireModelType(tenantId, conversationModelId, "CHAT", "conversationModelId");
                requireModelType(tenantId, asrModelId, "ASR", "asrModelId");
                requireModelType(tenantId, ttsModelId, "TTS", "ttsModelId");
            }
            default -> throw invalidParamException("Unsupported realtime mode: {}", mode);
        }
    }

    private void validateOptionalModel(long tenantId, Long modelId, String expectedType, String field) {
        if (modelId != null) {
            requireModelType(tenantId, modelId, expectedType, field);
        }
    }

    private AiModelDO requireModelType(long tenantId, Long modelId, String expectedType, String field) {
        if (modelId == null) {
            throw invalidParamException("{} is required for selected realtime mode", field);
        }
        AiModelDO model = modelMapper.selectByIdAndTenantId(modelId, tenantId);
        if (model == null) {
            throw invalidParamException("{} does not exist in current tenant", field);
        }
        if (!expectedType.equals(model.getModelType())) {
            throw invalidParamException("{} must reference model type {}", field, expectedType);
        }
        return model;
    }

    private AiPromptDO requireSystemPrompt(long tenantId, long promptId) {
        AiPromptDO prompt = promptMapper.selectByIdAndTenantId(promptId, tenantId);
        if (prompt == null) {
            throw invalidParamException("System prompt does not exist in current tenant");
        }
        if (!"SYSTEM".equals(prompt.getType())) {
            throw invalidParamException("systemPromptId must reference a SYSTEM prompt");
        }
        return prompt;
    }

    private AiAgentDO requireAgent(long tenantId, long id) {
        AiAgentDO row = agentMapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI agent does not exist");
        }
        return row;
    }

    private static void apply(AiAgentDO row, long tenantId, String name, String code, String description,
                              long promptId, Long conversationModelId, Long realtimeModelId,
                              Long asrModelId, Long ttsModelId, String realtimeMode, String memoryMode,
                              boolean memoryReadEnabled, boolean memoryWriteEnabled, boolean knowledgeEnabled,
                              String voiceConfigJson, String status) {
        row.setTenantId(tenantId);
        row.setName(name.trim());
        row.setCode(code.trim());
        row.setDescription(description);
        row.setSystemPromptId(promptId);
        row.setConversationModelId(conversationModelId);
        row.setRealtimeModelId(realtimeModelId);
        row.setAsrModelId(asrModelId);
        row.setTtsModelId(ttsModelId);
        row.setRealtimeMode(realtimeMode);
        row.setMemoryMode(memoryMode);
        row.setMemoryReadEnabled(memoryReadEnabled);
        row.setMemoryWriteEnabled(memoryWriteEnabled);
        row.setKnowledgeEnabled(knowledgeEnabled);
        row.setVoiceConfigJson(voiceConfigJson);
        row.setStatus(status == null || status.isBlank()
                ? DEFAULT_STATUS : status.trim().toUpperCase(Locale.ROOT));
    }

    private static String normalize(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            throw invalidParamException("Agent {} must not be blank", label);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalidParamException("Unsupported agent {}: {}", label, normalized);
        }
        return normalized;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalidParamException(message);
        }
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }

    private record ValidatedAgentConfig(String realtimeMode, String memoryMode) {
    }
}
