package com.robot.platform.robot.mission.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("robot_mission_action")
public class MissionActionDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long missionId;
    private Integer sequenceNo;
    private String actionType;
    private String parameters;
    private String status;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private String errorCode;
    private String errorMessage;
}
