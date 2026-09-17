package com.robot.platform.robot.message.inbox.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Durable idempotency fact. A duplicate key means only that this message was previously accepted. */
@Data
@TableName("robot_message_inbox")
public class RobotMessageInboxDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long deviceId;
    private String messageId;
    private String requestId;
    private String topic;
    private String messageType;
    private String payloadHash;
    private String result;
}
