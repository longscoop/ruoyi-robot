package com.robot.platform.integration;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.framework.mq.redis.core.RedisMQTemplate;
import com.robot.platform.framework.websocket.core.sender.local.LocalWebSocketMessageSender;
import com.robot.platform.framework.websocket.core.sender.redis.RedisWebSocketMessage;
import com.robot.platform.framework.websocket.core.sender.redis.RedisWebSocketMessageConsumer;
import com.robot.platform.framework.websocket.core.sender.redis.RedisWebSocketMessageSender;
import com.robot.platform.framework.websocket.core.session.WebSocketSessionManagerImpl;
import com.robot.platform.framework.websocket.core.util.WebSocketFrameworkUtils;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the exact same user id in two tenants, which was the previous bypass in session bucketing. */
class RobotWebSocketTenantIsolationIT {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void tenantBPublicationCannotReachTenantASessionWithSameUserId() throws Exception {
        WebSocketSessionManagerImpl sessions = new WebSocketSessionManagerImpl();
        WebSocketSession a = session("a", 10L, 99L); WebSocketSession b = session("b", 20L, 99L);
        sessions.addSession(a); sessions.addSession(b);
        TenantContextHolder.setTenantId(20L);

        new LocalWebSocketMessageSender(sessions).send(WebSocketFrameworkUtils.getLoginUserType(a), 99L, "ROBOT_STATUS_CHANGED", "{}");

        verify(b).sendMessage(any()); verify(a, never()).sendMessage(any());
    }
    @Test void redisConsumerScopesTenantAndClearsReusedThread() {
        RedisWebSocketMessageSender sender = mock(RedisWebSocketMessageSender.class);
        doAnswer(invocation -> { org.assertj.core.api.Assertions.assertThat(TenantContextHolder.getTenantId()).isEqualTo(20L); return null; })
                .when(sender).send(any(), any(), any(), any(), any());
        RedisWebSocketMessage event = new RedisWebSocketMessage(); event.setTenantId(20L); event.setMessageType("ROBOT_STATUS_CHANGED"); event.setMessageContent("{}");

        new RedisWebSocketMessageConsumer(sender).onMessage(event);

        verify(sender).send(null, null, null, "ROBOT_STATUS_CHANGED", "{}");
        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.getTenantId()).isNull();
    }
    @Test void redisPublisherCarriesTheExplicitTenantBoundary() {
        RedisMQTemplate redis = mock(RedisMQTemplate.class);
        RedisWebSocketMessageSender sender = new RedisWebSocketMessageSender(new WebSocketSessionManagerImpl(), redis);
        TenantContextHolder.setTenantId(20L);

        sender.send(com.robot.platform.framework.common.enums.UserTypeEnum.ADMIN.getValue(),
                "ROBOT_STATUS_CHANGED", "{}");

        org.mockito.ArgumentCaptor<RedisWebSocketMessage> message = org.mockito.ArgumentCaptor.forClass(RedisWebSocketMessage.class);
        verify(redis).send(message.capture());
        org.assertj.core.api.Assertions.assertThat(message.getValue().getTenantId()).isEqualTo(20L);
    }
    @Test void redisConsumerRestoresAnExistingWorkerTenant() {
        RedisWebSocketMessageSender sender = mock(RedisWebSocketMessageSender.class);
        RedisWebSocketMessage event = new RedisWebSocketMessage().setTenantId(20L)
                .setMessageType("ROBOT_STATUS_CHANGED").setMessageContent("{}");
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);

        new RedisWebSocketMessageConsumer(sender).onMessage(event);

        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.isIgnore()).isTrue();
    }
    private static WebSocketSession session(String id, long tenant, long userId) {
        WebSocketSession session = mock(WebSocketSession.class); Map<String, Object> attributes = new HashMap<>();
        WebSocketFrameworkUtils.setLoginUser(new RobotAuthenticatedPrincipal(tenant, userId, ApiAudience.APP, SubjectType.MEMBER), attributes);
        when(session.getAttributes()).thenReturn(attributes); when(session.getId()).thenReturn(id); when(session.isOpen()).thenReturn(true);
        return session;
    }
}
