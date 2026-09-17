package com.robot.platform.robot.realtime.outbox.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable, tenant-routed websocket event. Delivery is at-least-once with a stable event key. */
@Data
@TableName("robot_realtime_event_outbox")
public class RobotRealtimeEventOutboxDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long robotId;
    private String eventKey;
    private String eventType;
    private String payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime claimedAt;
    private String lastError;
}
