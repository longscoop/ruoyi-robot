package com.robot.platform.ai.realtime.service;

import com.robot.platform.ai.realtime.dal.dataobject.AiRealtimeSessionDO;

import java.time.LocalDateTime;

public interface AiRealtimeSessionService {

    long startRealtimeSession(RealtimeSessionStart start);

    AiRealtimeSessionDO getRealtimeSession(long tenantId, long id);

    void finishRealtimeSession(long tenantId, long id, RealtimeSessionFinish finish);

    record RealtimeSessionStart(long tenantId, long conversationId, long agentId,
                                long robotId, Long memberId, String mode, Long providerId,
                                Long modelId, String providerSessionId) {
    }

    record RealtimeSessionFinish(String status, String errorCode,
                                 LocalDateTime firstAudioAt, LocalDateTime firstResponseAt,
                                 Integer interruptCount, LocalDateTime endedAt) {
    }
}
