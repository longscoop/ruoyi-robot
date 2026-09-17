package com.robot.platform.robot.mission.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable tenant-owned mission. The version is incremented only by guarded state writes. */
@Data
@TableName("robot_mission")
public class MissionDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private String missionNo;
    private Long robotId;
    private String missionType;
    private String source;
    private String status;
    private Integer priority;
    private String requestId;
    private Long creatorId;
    private LocalDateTime scheduledTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime cancelRequestedTime;
    private String payload;
    private String errorCode;
    private String errorMessage;
    private Integer version;
}
