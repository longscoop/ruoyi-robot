package com.robot.platform.robot.status;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.job.RobotOfflineJob;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import com.robot.platform.robot.status.service.RobotOfflineTransitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RobotOfflineJobTest {
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final Duration TTL = Duration.ofMinutes(10);
    private final RobotMapper robots = mock(RobotMapper.class);
    private final RobotLiveStatusStore statuses = mock(RobotLiveStatusStore.class);
    private final RobotRealtimeEventPublisher events = mock(RobotRealtimeEventPublisher.class);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void atomicallyComparesSchemaHeartbeatAndCutoffBeforePersistingOffline() {
        Instant heartbeatAt = NOW.minus(TIMEOUT);
        RobotDO robot = onlineRobot(heartbeatAt);
        RobotLiveStatus live = live(RobotOnlineStatus.ONLINE, heartbeatAt);
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of(robot));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);
        when(statuses.get(10, 7)).thenReturn(Optional.of(live));
        when(statuses.transitionOffline(10, 7, 1, heartbeatAt, heartbeatAt, TTL)).thenReturn(true);
        when(robots.markOfflineIfHeartbeatBefore(10, 7, LocalDateTime.ofInstant(heartbeatAt, ZoneOffset.UTC))).thenReturn(1);

        job().scan();

        verify(statuses).transitionOffline(10, 7, 1, heartbeatAt, heartbeatAt, TTL);
        verify(events).publish(argThat(event -> event.tenantId() == 10 && event.robotId() == 7
                && event.data().onlineStatus() == RobotOnlineStatus.OFFLINE
                && event.data().lastHeartbeatAt().equals(heartbeatAt)));
    }

    @Test
    void missingRedisProjectionFallsBackToDurableConditionalUpdate() {
        Instant heartbeatAt = NOW.minus(Duration.ofMinutes(3));
        RobotDO robot = onlineRobot(heartbeatAt);
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of(robot));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);
        when(statuses.get(10, 7)).thenReturn(Optional.empty());
        when(statuses.restoreOfflineIfAbsent(eq(10L), eq(7L), any(), eq(TTL))).thenReturn(true);
        when(robots.markOfflineIfHeartbeatBefore(eq(10L), eq(7L), any())).thenReturn(1);

        job().scan();

        verify(robots).markOfflineIfHeartbeatBefore(10, 7,
                LocalDateTime.ofInstant(NOW.minus(TIMEOUT), ZoneOffset.UTC));
        verify(statuses).restoreOfflineIfAbsent(eq(10L), eq(7L),
                argThat(status -> status.onlineStatus() == RobotOnlineStatus.OFFLINE), eq(TTL));
        verify(events).publish(any(TenantRobotRealtimeEvent.class));
    }

    @Test
    void concurrentHeartbeatCreatingMissingProjectionIsNeverOverwrittenOrReportedOffline() {
        Instant heartbeatAt = NOW.minus(Duration.ofMinutes(3));
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of(onlineRobot(heartbeatAt)));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(onlineRobot(heartbeatAt));
        when(statuses.get(10, 7)).thenReturn(Optional.empty());
        when(robots.markOfflineIfHeartbeatBefore(eq(10L), eq(7L), any())).thenReturn(1);
        // A heartbeat wrote a new ONLINE projection after the scanner observed the missing key.
        when(statuses.restoreOfflineIfAbsent(eq(10L), eq(7L), any(), eq(TTL))).thenReturn(false);

        job().scan();

        verify(statuses, never()).put(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void databaseFailureAfterRedisCasIsRetriedWhenProjectionIsAlreadyOffline() {
        Instant heartbeatAt = NOW.minus(Duration.ofMinutes(3));
        RobotDO robot = onlineRobot(heartbeatAt);
        RobotLiveStatus online = live(RobotOnlineStatus.ONLINE, heartbeatAt);
        RobotLiveStatus offline = live(RobotOnlineStatus.OFFLINE, heartbeatAt);
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of(robot));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);
        when(statuses.get(10, 7)).thenReturn(Optional.of(online), Optional.of(offline));
        when(statuses.transitionOffline(eq(10L), eq(7L), anyInt(), any(), any(), eq(TTL))).thenReturn(true);
        when(robots.markOfflineIfHeartbeatBefore(eq(10L), eq(7L), any()))
                .thenThrow(new IllegalStateException("db unavailable"))
                .thenReturn(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> job().scan())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db unavailable");
        job().scan();

        verify(robots, times(2)).markOfflineIfHeartbeatBefore(eq(10L), eq(7L), any());
        verify(events, times(1)).publish(any(TenantRobotRealtimeEvent.class));
    }

    @Test
    void freshHeartbeatWinningRedisCasPreventsDatabaseOfflineWrite() {
        Instant observed = NOW.minus(Duration.ofMinutes(3));
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of(onlineRobot(observed)));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(onlineRobot(observed));
        when(statuses.get(10, 7)).thenReturn(Optional.of(live(RobotOnlineStatus.ONLINE, observed)));
        when(statuses.transitionOffline(eq(10L), eq(7L), anyInt(), eq(observed), any(), eq(TTL))).thenReturn(false);

        job().scan();

        verify(robots, never()).markOfflineIfHeartbeatBefore(anyLong(), anyLong(), any());
        verifyNoInteractions(events);
    }

    @Test
    void scanRestoresTheWorkerTenantContext() {
        TenantContextHolder.setTenantId(99L);
        when(robots.selectOnlineCandidatesIgnoringTenant()).thenReturn(List.of());

        job().scan();

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
    }

    private RobotOfflineJob job() {
        return new RobotOfflineJob(robots, statuses, TTL, TIMEOUT,
                new RobotOfflineTransitionService(robots, statuses, events), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RobotDO onlineRobot(Instant heartbeatAt) {
        RobotDO robot = new RobotDO();
        robot.setId(7L);
        robot.setTenantId(10L);
        robot.setOnlineStatus(RobotOnlineStatus.ONLINE.name());
        robot.setWorkStatus(RobotWorkStatus.IDLE.name());
        robot.setBatteryLevel(71);
        robot.setIpAddress("127.0.0.1");
        robot.setSoftwareVersion("1.0");
        robot.setLastHeartbeatTime(LocalDateTime.ofInstant(heartbeatAt, ZoneOffset.UTC));
        return robot;
    }

    private static RobotLiveStatus live(RobotOnlineStatus onlineStatus, Instant heartbeatAt) {
        return new RobotLiveStatus(1, onlineStatus, RobotWorkStatus.IDLE, 71, 20, 30, 25,
                "127.0.0.1", null, "1.0", heartbeatAt, heartbeatAt.toEpochMilli());
    }
}
