package com.robot.platform.ai.digitalhuman.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("ai_digital_human")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiDigitalHumanDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    private Long agentId;
    private String avatarType;
    private String avatarUrl;
    private String avatarResourceUrl;
    private String coverUrl;
    private Long voiceModelId;
    private String voiceId;
    private BigDecimal speechRate;
    private BigDecimal pitch;
    private BigDecimal volume;
    private String lipSyncMode;
    private String welcomeText;
    private Boolean interruptEnabled;
    private String configJson;
    private String status;
}
