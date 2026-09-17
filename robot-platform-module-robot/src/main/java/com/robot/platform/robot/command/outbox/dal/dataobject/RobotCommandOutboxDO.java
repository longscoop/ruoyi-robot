package com.robot.platform.robot.command.outbox.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable cloud-to-robot delivery record. A command is never lost merely because MQTT is unavailable. */
@Data
@TableName("robot_command_outbox")
public class RobotCommandOutboxDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long deviceId;
    private Long robotId;
    private Long missionId;
    private String messageId;
    private String requestId;
    private String topic;
    private String envelope;
    private String messageType;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime claimedAt;
    private String lastError;
}
