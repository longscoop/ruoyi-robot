package com.robot.platform.ai.realtime.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("ai_realtime_session")
@Data
public class AiRealtimeSessionDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long conversationId;
    private Long agentId;
    private Long robotId;
    private Long memberId;
    private String mode;
    private Long providerId;
    private Long modelId;
    private String providerSessionId;
    private LocalDateTime connectedAt;
    private LocalDateTime firstAudioAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime endedAt;
    private Integer interruptCount;
    private String errorCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
