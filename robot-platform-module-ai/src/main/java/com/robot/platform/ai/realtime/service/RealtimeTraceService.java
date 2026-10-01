package com.robot.platform.ai.realtime.service;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.conversation.service.AiConversationService;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMapper;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.realtime.runtime.RealtimeRoute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persists the same lifecycle used by the live device runtime. Never stores raw audio or credentials. */
@Service
public class RealtimeTraceService {
    private final AiConversationService conversations;
    private final AiRealtimeSessionService sessions;
    private final AiConversationMapper conversationMapper;
    private final AiModelMapper models;

    public RealtimeTraceService(AiConversationService conversations, AiRealtimeSessionService sessions,
                                AiConversationMapper conversationMapper, AiModelMapper models) {
        this.conversations = conversations;
        this.sessions = sessions;
        this.conversationMapper = conversationMapper;
        this.models = models;
    }

    public record Trace(long tenantId, long conversationId, long realtimeSessionId, Long modelId) { }

    @Transactional
    public Trace start(AiAgentConfig agent, ConversationIdentity identity, RealtimeRoute route) {
        long tenant = identity.tenantId();
        Long modelId = route.mode() == RealtimeRoute.Mode.NATIVE ? route.realtimeModelId() : route.conversationModelId();
        var model = modelId == null ? null : models.selectByIdAndTenantId(modelId, tenant);
        long conversation = conversations.startConversation(tenant, agent.agentId(), identity.robotId(),
                identity.memberId(), "ROBOT_VOICE");
        long session = sessions.startRealtimeSession(new AiRealtimeSessionService.RealtimeSessionStart(
                tenant, conversation, agent.agentId(), identity.robotId(), identity.memberId(), route.mode().name(),
                model == null ? null : model.getProviderId(), modelId, null));
        return new Trace(tenant, conversation, session, modelId);
    }

    public void message(Trace trace, String turnId, String role, String text) {
        if (text == null || text.isBlank()) return;
        conversations.appendMessage(new AiConversationService.AiConversationMessage(trace.tenantId(),
                trace.conversationId(), turnId, role, text, trace.modelId(), null, null, null, null));
    }

    @Transactional
    public void finish(Trace trace, String errorCode, LocalDateTime firstAudioAt,
                       LocalDateTime firstResponseAt, int interrupts) {
        LocalDateTime now = LocalDateTime.now();
        String status = errorCode == null ? "CLOSED" : "ERROR";
        sessions.finishRealtimeSession(trace.tenantId(), trace.realtimeSessionId(),
                new AiRealtimeSessionService.RealtimeSessionFinish(status, errorCode,
                        firstAudioAt, firstResponseAt, interrupts, now));
        var conversation = conversations.getConversation(trace.tenantId(), trace.conversationId());
        conversation.setStatus(status);
        conversation.setEndedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
    }
}
