package com.robot.platform.robot.mission.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Append-only state/audit timeline. Payload is a redacted representation, never credentials. */
@Data
@TableName("robot_mission_event")
public class MissionEventDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long missionId;
    private Long actionId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String messageId;
    private String requestId;
    private String payload;
    private LocalDateTime occurredTime;
}
