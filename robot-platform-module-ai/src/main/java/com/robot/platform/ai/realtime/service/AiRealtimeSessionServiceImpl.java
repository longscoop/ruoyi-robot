package com.robot.platform.ai.realtime.service;

import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMapper;
import com.robot.platform.ai.realtime.dal.dataobject.AiRealtimeSessionDO;
import com.robot.platform.ai.realtime.dal.mysql.AiRealtimeSessionMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiRealtimeSessionServiceImpl implements AiRealtimeSessionService {
    private static final Set<String> MODES = Set.of("NATIVE", "CASCADE");

    private final AiRealtimeSessionMapper realtimeSessionMapper;
    private final AiConversationMapper conversationMapper;

    public AiRealtimeSessionServiceImpl(AiRealtimeSessionMapper realtimeSessionMapper,
                                        AiConversationMapper conversationMapper) {
        this.realtimeSessionMapper = realtimeSessionMapper;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public long startRealtimeSession(RealtimeSessionStart start) {
        if (start == null) {
            throw invalidParamException("Realtime session start must not be null");
        }
        requireTenant(start.tenantId());
        AiConversationDO conversation = requireConversation(start.tenantId(), start.conversationId());
        if (!Long.valueOf(start.agentId()).equals(conversation.getAgentId())
                || !Long.valueOf(start.robotId()).equals(conversation.getRobotId())) {
            throw invalidParamException("Realtime session must match conversation agent and robot");
        }

        String mode = normalizeMode(start.mode());
        LocalDateTime now = LocalDateTime.now();
        AiRealtimeSessionDO row = new AiRealtimeSessionDO();
        row.setTenantId(start.tenantId());
        row.setConversationId(start.conversationId());
        row.setAgentId(start.agentId());
        row.setRobotId(start.robotId());
        row.setMemberId(start.memberId());
        row.setMode(mode);
        row.setProviderId(start.providerId());
        row.setModelId(start.modelId());
        row.setProviderSessionId(start.providerSessionId());
        row.setConnectedAt(now);
        row.setInterruptCount(0);
        row.setStatus("CONNECTED");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        realtimeSessionMapper.insert(row);
        return requireGeneratedId(row.getId());
    }

    @Override
    public AiRealtimeSessionDO getRealtimeSession(long tenantId, long id) {
        requireTenant(tenantId);
        return requireSession(tenantId, id);
    }

    @Override
    public void finishRealtimeSession(long tenantId, long id, RealtimeSessionFinish finish) {
        requireTenant(tenantId);
        if (finish == null) {
            throw invalidParamException("Realtime session finish must not be null");
        }
        AiRealtimeSessionDO row = requireSession(tenantId, id);
        if (finish.status() == null || finish.status().isBlank()) {
            throw invalidParamException("Realtime session status must not be blank");
        }

        row.setStatus(finish.status().trim().toUpperCase(Locale.ROOT));
        row.setErrorCode(finish.errorCode());
        if (finish.firstAudioAt() != null) {
            row.setFirstAudioAt(finish.firstAudioAt());
        }
        if (finish.firstResponseAt() != null) {
            row.setFirstResponseAt(finish.firstResponseAt());
        }
        if (finish.interruptCount() != null) {
            if (finish.interruptCount() < 0) {
                throw invalidParamException("Realtime interrupt count must not be negative");
            }
            row.setInterruptCount(finish.interruptCount());
        }
        row.setEndedAt(finish.endedAt() == null ? LocalDateTime.now() : finish.endedAt());
        row.setUpdatedAt(LocalDateTime.now());
        realtimeSessionMapper.updateById(row);
    }

    private AiConversationDO requireConversation(long tenantId, long id) {
        AiConversationDO row = conversationMapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI conversation does not exist");
        }
        return row;
    }

    private AiRealtimeSessionDO requireSession(long tenantId, long id) {
        AiRealtimeSessionDO row = realtimeSessionMapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI realtime session does not exist");
        }
        return row;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw invalidParamException("Realtime session mode must not be blank");
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(normalized)) {
            throw invalidParamException("Unsupported realtime session mode: {}", normalized);
        }
        return normalized;
    }

    private static long requireGeneratedId(Long id) {
        if (id == null) {
            throw invalidParamException("Failed to persist AI realtime session");
        }
        return id;
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }
}
