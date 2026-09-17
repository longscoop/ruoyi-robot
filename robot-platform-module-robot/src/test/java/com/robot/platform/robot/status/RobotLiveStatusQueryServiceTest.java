package com.robot.platform.robot.status;

import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RobotLiveStatusQueryServiceTest {
    private final RobotLiveStatusStore statuses = mock(RobotLiveStatusStore.class);
    private final RobotMapper robots = mock(RobotMapper.class);
    private final RobotLiveStatusQueryService queries = new RobotLiveStatusQueryService(statuses, robots);

    @Test
    void fallsBackToTheTenantScopedDatabaseSnapshotWhenRedisIsMissing() {
        RobotDO robot = new RobotDO();
        robot.setId(7L); robot.setTenantId(10L); robot.setOnlineStatus("OFFLINE"); robot.setWorkStatus("IDLE");
        robot.setBatteryLevel(64); robot.setIpAddress("127.0.0.1"); robot.setSoftwareVersion("1.0");
        robot.setLastHeartbeatTime(LocalDateTime.of(2026, 9, 13, 12, 0));
        when(statuses.get(10, 7)).thenReturn(Optional.empty());
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);

        var result = queries.find(10, 7);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
        assertThat(result.orElseThrow().workStatus()).isEqualTo(RobotWorkStatus.IDLE);
        assertThat(result.orElseThrow().batteryLevel()).isEqualTo(64);
    }

    @Test
    void neverReturnsAnotherTenantsDatabaseSnapshot() {
        when(statuses.get(10, 7)).thenReturn(Optional.empty());
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(null);

        assertThat(queries.find(10, 7)).isEmpty();

        verify(robots).selectByTenantAndIdForUpdate(10, 7);
    }

    @Test
    void redisFailureFallsBackToTheDurableDatabaseSnapshot() {
        RobotDO robot = new RobotDO();
        robot.setId(7L); robot.setTenantId(10L); robot.setOnlineStatus("ONLINE"); robot.setWorkStatus("IDLE");
        when(statuses.get(10, 7)).thenThrow(new IllegalStateException("redis unavailable"));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);

        assertThat(queries.find(10, 7)).isPresent();
    }

    @Test
    void staleOnlineProjectionIsReturnedAsOfflineAndRepairedFromTheDurableSnapshot() {
        RobotDO robot = new RobotDO();
        robot.setId(7L); robot.setTenantId(10L); robot.setOnlineStatus("OFFLINE"); robot.setWorkStatus("IDLE");
        robot.setLastHeartbeatTime(LocalDateTime.of(2026, 9, 13, 12, 0));
        Instant staleAt = robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC);
        var staleOnline = new com.robot.platform.robot.status.model.RobotLiveStatus(1, RobotOnlineStatus.ONLINE,
                RobotWorkStatus.IDLE, 64, 0, 0, 0, null, null, "1.0", staleAt, staleAt.toEpochMilli());
        when(statuses.get(10, 7)).thenReturn(Optional.of(staleOnline));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);

        var result = queries.find(10, 7);

        assertThat(result).hasValueSatisfying(status -> assertThat(status.onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE));
        var ordered = inOrder(robots, statuses);
        ordered.verify(robots).selectByTenantAndIdForUpdate(10, 7);
        ordered.verify(statuses).get(10, 7);
        ordered.verify(statuses).transitionOffline(eq(10L), eq(7L), eq(1), eq(staleAt), eq(staleAt), any());
    }

    @Test
    void throttledHeartbeatNewerThanDatabaseSnapshotCannotResurrectDurableOfflineRobot() {
        RobotDO robot = new RobotDO();
        robot.setId(7L); robot.setTenantId(10L); robot.setOnlineStatus("OFFLINE"); robot.setWorkStatus("IDLE");
        robot.setLastHeartbeatTime(LocalDateTime.of(2026, 9, 13, 12, 0));
        Instant freshAt = robot.getLastHeartbeatTime().toInstant(ZoneOffset.UTC).plusSeconds(1);
        var freshOnline = new com.robot.platform.robot.status.model.RobotLiveStatus(1, RobotOnlineStatus.ONLINE,
                RobotWorkStatus.IDLE, 64, 0, 0, 0, null, null, "1.0", freshAt, freshAt.toEpochMilli());
        when(statuses.get(10, 7)).thenReturn(Optional.of(freshOnline));
        when(robots.selectByTenantAndIdForUpdate(10, 7)).thenReturn(robot);

        assertThat(queries.find(10, 7)).hasValueSatisfying(status ->
                assertThat(status.onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE));
        verify(statuses, never()).put(anyLong(), anyLong(), any(), any());
    }
}
