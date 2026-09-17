package com.robot.platform.robot.robot.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Builder;

/** Persisted, tenant-scoped capability declaration; it is not a synthetic live status. */
@TableName("robot_capability")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotCapabilityDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long robotId;
    private String capabilityCode;
    private String configuration;
}
