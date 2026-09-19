package com.robot.platform.ai.conversation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("ai_conversation_message")
@Data
public class AiConversationMessageDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long conversationId;
    private String turnId;
    private String role;
    private String content;
    private Long modelId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
    private String metadataJson;
    private LocalDateTime createdAt;
}
