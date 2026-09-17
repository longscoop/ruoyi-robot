package com.robot.platform.ai.prompt.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@TableName("ai_prompt")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiPromptDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String type;
    private String content;
    private Integer version;
    private String status;
}
