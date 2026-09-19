package com.robot.platform.ai.conversation.service;

import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;
import com.robot.platform.ai.conversation.dal.dataobject.AiConversationMessageDO;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMapper;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMessageMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiConversationServiceImpl implements AiConversationService {
    private static final Set<String> CHANNELS = Set.of("ROBOT_VOICE", "APP_TEXT", "APP_VOICE", "WEB");
    private static final Set<String> ROLES = Set.of("SYSTEM", "USER", "ASSISTANT", "TOOL_CALL", "TOOL_RESULT");

    private final AiConversationMapper conversationMapper;
    private final AiConversationMessageMapper messageMapper;

    public AiConversationServiceImpl(AiConversationMapper conversationMapper,
                                     AiConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public long startConversation(long tenantId, long agentId, long robotId, Long memberId, String channel) {
        requireTenant(tenantId);
        String normalizedChannel = normalize(channel, CHANNELS, "conversation channel");
        LocalDateTime now = LocalDateTime.now();

        AiConversationDO row = new AiConversationDO();
        row.setTenantId(tenantId);
        row.setAgentId(agentId);
        row.setRobotId(robotId);
        row.setMemberId(memberId);
        row.setChannel(normalizedChannel);
        row.setStatus("ACTIVE");
        row.setStartedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        conversationMapper.insert(row);
        return requireGeneratedId(row.getId(), "conversation");
    }

    @Override
    public AiConversationDO getConversation(long tenantId, long id) {
        requireTenant(tenantId);
        return requireConversation(tenantId, id);
    }

    @Override
    public void appendMessage(AiConversationMessage message) {
        if (message == null) {
            throw invalidParamException("Conversation message must not be null");
        }
        requireTenant(message.tenantId());
        requireConversation(message.tenantId(), message.conversationId());
        String role = normalize(message.role(), ROLES, "conversation message role");
        if (message.content() == null) {
            throw invalidParamException("Conversation message content must not be null");
        }

        AiConversationMessageDO row = new AiConversationMessageDO();
        row.setTenantId(message.tenantId());
        row.setConversationId(message.conversationId());
        row.setTurnId(message.turnId());
        row.setRole(role);
        row.setContent(message.content());
        row.setModelId(message.modelId());
        row.setInputTokens(message.inputTokens());
        row.setOutputTokens(message.outputTokens());
        row.setLatencyMs(message.latencyMs());
        row.setMetadataJson(message.metadataJson());
        row.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(row);
    }

    @Override
    public List<AiConversationMessageDO> listMessages(long tenantId, long conversationId) {
        requireTenant(tenantId);
        requireConversation(tenantId, conversationId);
        return messageMapper.selectByConversationIdAndTenantId(conversationId, tenantId);
    }

    private AiConversationDO requireConversation(long tenantId, long id) {
        AiConversationDO row = conversationMapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI conversation does not exist");
        }
        return row;
    }

    private static String normalize(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            throw invalidParamException("{} must not be blank", label);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalidParamException("Unsupported {}: {}", label, normalized);
        }
        return normalized;
    }

    private static long requireGeneratedId(Long id, String label) {
        if (id == null) {
            throw invalidParamException("Failed to persist AI {}", label);
        }
        return id;
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }
}
