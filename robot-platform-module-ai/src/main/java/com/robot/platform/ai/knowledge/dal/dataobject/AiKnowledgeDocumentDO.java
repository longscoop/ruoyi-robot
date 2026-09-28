package com.robot.platform.ai.knowledge.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
public class AiKnowledgeDocumentDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long baseId;
    private String title;
    private String content;
}
