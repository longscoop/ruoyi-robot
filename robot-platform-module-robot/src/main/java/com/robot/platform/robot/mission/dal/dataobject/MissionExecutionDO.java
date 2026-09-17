package com.robot.platform.robot.mission.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("robot_mission_execution")
public class MissionExecutionDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long missionId;
    private Integer attemptNo;
    private String commandMessageId;
    private String commandType;
    private LocalDateTime acknowledgedTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private String result;
    private String errorCode;
    private String errorMessage;
}
