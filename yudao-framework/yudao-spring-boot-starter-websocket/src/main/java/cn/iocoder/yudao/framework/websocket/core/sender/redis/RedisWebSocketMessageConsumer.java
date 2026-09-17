package cn.iocoder.yudao.framework.websocket.core.sender.redis;

import cn.iocoder.yudao.framework.mq.redis.core.pubsub.AbstractRedisChannelMessageListener;
import lombok.RequiredArgsConstructor;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;

/**
 * {@link RedisWebSocketMessage} 广播消息的消费者，真正把消息发送出去
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor
public class RedisWebSocketMessageConsumer extends AbstractRedisChannelMessageListener<RedisWebSocketMessage> {

    private final RedisWebSocketMessageSender redisWebSocketMessageSender;

    @Override
    public void onMessage(RedisWebSocketMessage message) {
        if (message.getTenantId() == null) throw new IllegalArgumentException("Redis WebSocket message misses tenant");
        TenantUtils.execute(message.getTenantId(), () -> redisWebSocketMessageSender.send(message.getSessionId(),
                message.getUserType(), message.getUserId(), message.getMessageType(), message.getMessageContent()));
    }

}
