package com.robot.platform.robot.robot.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Tenant-owned robot. Unlike inventory devices, a robot never exists outside a tenant.
 * Online and work status are separate because a disconnected robot can still have a
 * last known work state; neither field is inferred by ordinary CRUD.
 */
@TableName("robot")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private Long productId;
    private String robotCode;
    private String name;
    private String onlineStatus;
    private String workStatus;
    private Integer batteryLevel;
    private String ipAddress;
    private String softwareVersion;
    private String currentMissionId;
    private LocalDateTime lastHeartbeatTime;
}
