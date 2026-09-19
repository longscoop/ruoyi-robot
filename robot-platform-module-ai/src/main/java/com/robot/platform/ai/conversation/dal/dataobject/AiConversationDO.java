package com.robot.platform.ai.conversation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("ai_conversation")
@Data
public class AiConversationDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long agentId;
    private Long robotId;
    private Long memberId;
    private String channel;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
