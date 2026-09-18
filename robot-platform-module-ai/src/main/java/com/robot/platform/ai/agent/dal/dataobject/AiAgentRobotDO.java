package com.robot.platform.ai.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ai_agent_robot")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiAgentRobotDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long agentId;
    private Long robotId;
    private Boolean isDefault;
    private String status;
}
