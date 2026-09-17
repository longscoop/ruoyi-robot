package com.robot.platform.framework.websocket.core.sender.redis;

import com.robot.platform.framework.mq.redis.core.pubsub.AbstractRedisChannelMessage;
import lombok.Data;

/**
 * Redis 广播 WebSocket 的消息
 */
@Data
public class RedisWebSocketMessage extends AbstractRedisChannelMessage {

    /**
     * Session 编号
     */
    private String sessionId;
    /**
     * 用户类型
     */
    private Integer userType;
    /**
     * 用户编号
     */
    private Long userId;
    /** Mandatory routing boundary. A cluster node must never infer a tenant from user id. */
    private Long tenantId;

    /**
     * 消息类型
     */
    private String messageType;
    /**
     * 消息内容
     */
    private String messageContent;

}
