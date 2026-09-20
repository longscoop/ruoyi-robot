package com.robot.platform.ai.memory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_memory")
@Data
public class AiMemoryDO {
    @TableId
    private Long id;
    private Long tenantId;
    private String scope;
    private Long memberId;
    private Long robotId;
    private String memoryType;
    private String content;
    private String summary;
    private BigDecimal importance;
    private BigDecimal confidence;
    private Long sourceConversationId;
    private Long sourceMessageId;
    private LocalDateTime firstObservedAt;
    private LocalDateTime lastObservedAt;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
