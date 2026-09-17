package com.robot.platform.ai.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@TableName("ai_agent")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    private Long systemPromptId;
    private Long conversationModelId;
    private Long realtimeModelId;
    private Long asrModelId;
    private Long ttsModelId;
    private String realtimeMode;
    private String memoryMode;
    private Boolean memoryReadEnabled;
    private Boolean memoryWriteEnabled;
    private Boolean knowledgeEnabled;
    private String voiceConfigJson;
    private String status;
}
