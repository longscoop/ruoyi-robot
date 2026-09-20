package com.robot.platform.ai.digitalhuman.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ai_digital_human_action")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiDigitalHumanActionDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long digitalHumanId;
    private String state;
    private String actionCode;
    private String configJson;
}
