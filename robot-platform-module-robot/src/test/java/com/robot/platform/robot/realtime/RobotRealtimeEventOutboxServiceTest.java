package com.robot.platform.robot.realtime;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.outbox.RobotRealtimeEventOutboxService;
import com.robot.platform.robot.realtime.outbox.dal.dataobject.RobotRealtimeEventOutboxDO;
import com.robot.platform.robot.realtime.outbox.dal.mysql.RobotRealtimeEventOutboxMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RobotRealtimeEventOutboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void publishFailureIsRetriedFromTheSameDurableRowAndSucceedsOnce() {
        RobotRealtimeEventOutboxMapper mapper = mock(RobotRealtimeEventOutboxMapper.class);
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        doThrow(new IllegalStateException("redis pubsub down")).doNothing().when(sender)
                .send(eq(UserTypeEnum.ADMIN.getValue()), eq("ROBOT_STATUS_CHANGED"), anyString());
        when(mapper.selectByIdIgnoringTenant(51L)).thenReturn(row(51L));
        when(mapper.claim(eq(51L), any(), any())).thenReturn(1, 1);
        when(mapper.markRetry(eq(51L), any(), any(), contains("redis pubsub down"))).thenReturn(1);
        when(mapper.markSent(eq(51L), any())).thenReturn(1);
        RobotRealtimeEventOutboxService service = service(mapper, sender);

        service.dispatchSafely(51L);
        service.dispatchSafely(51L);

        verify(sender, times(2)).send(eq(UserTypeEnum.ADMIN.getValue()), eq("ROBOT_STATUS_CHANGED"), anyString());
        verify(mapper).markRetry(eq(51L), any(), any(), contains("redis pubsub down"));
        verify(mapper).markSent(eq(51L), any());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test void dispatchRestoresTheCompletePreexistingTenantWorkerContext() {
        RobotRealtimeEventOutboxMapper mapper = mock(RobotRealtimeEventOutboxMapper.class);
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        when(mapper.selectByIdIgnoringTenant(52L)).thenReturn(row(52L));
        when(mapper.claim(eq(52L), any(), any())).thenReturn(1);
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);

        service(mapper, sender).dispatchSafely(52L);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test void afterCommitAcceleratorNeverEscapesAnOutboxDatabaseFailure() {
        RobotRealtimeEventOutboxMapper mapper = mock(RobotRealtimeEventOutboxMapper.class);
        when(mapper.claim(eq(53L), any(), any())).thenThrow(new IllegalStateException("mysql down"));
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);

        assertThatCode(() -> service(mapper, sender).dispatchSafely(53L)).doesNotThrowAnyException();
    }

    private static RobotRealtimeEventOutboxService service(RobotRealtimeEventOutboxMapper mapper,
                                                            WebSocketMessageSender sender) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(); beans.addBean("sender", sender);
        return new RobotRealtimeEventOutboxService(mapper, beans.getBeanProvider(WebSocketMessageSender.class),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO, Duration.ofMinutes(2));
    }

    private static RobotRealtimeEventOutboxDO row(long id) {
        RobotRealtimeEventOutboxDO row = new RobotRealtimeEventOutboxDO();
        row.setId(id); row.setTenantId(20L); row.setEventKey("REQ-1:ROBOT_STATUS_CHANGED");
        row.setEventType("ROBOT_STATUS_CHANGED");
        row.setPayload(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(event()));
        row.setStatus("RETRY"); row.setAttemptCount(1);
        row.setClaimedAt(java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static TenantRobotRealtimeEvent event() {
        RobotLiveStatus status = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                72, 30, 40, 25, "127.0.0.1", null, "1.0", NOW, NOW.toEpochMilli());
        return new TenantRobotRealtimeEvent(1, 20, 7, "REQ-1", "ROBOT_STATUS_CHANGED", NOW, status);
    }
}
