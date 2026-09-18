package com.robot.platform.ai.model.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ai_model")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long providerId;
    private String name;
    private String modelCode;
    private String modelType;
    private String capabilitiesJson;
    private String configJson;
    private String status;
}
