package com.robot.platform.robot.dashboard;

import com.robot.platform.robot.dashboard.model.RobotDashboard;
import com.robot.platform.robot.dashboard.service.RobotDashboardServiceImpl;
import com.robot.platform.robot.mission.dal.dataobject.MissionDashboardTrendDO;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotDashboardServiceTest {
    private final RobotMapper robots = mock(RobotMapper.class);
    private final MissionMapper missions = mock(MissionMapper.class);
    private final RobotLiveStatusQueryService liveStatuses = mock(RobotLiveStatusQueryService.class);
    private final RobotDashboardServiceImpl service = new RobotDashboardServiceImpl(robots, missions, liveStatuses,
            Clock.fixed(Instant.parse("2026-09-15T16:30:00Z"), ZoneId.of("UTC")), ZoneId.of("Asia/Shanghai"));

    @Test
    void aggregatesOnlyCurrentTenantRealData() {
        long tenantA = 10L;
        long tenantB = 20L;
        RobotDO tenantBRobot = robot(99L, tenantB, "B-ROBOT"); // Deliberately never returned by tenant A query.
        when(robots.selectDashboardRobots(tenantA)).thenReturn(List.of(
                robot(1L, tenantA, "A-1"), robot(2L, tenantA, "A-2"), robot(3L, tenantA, "A-3")));
        when(robots.countDashboardRobots(tenantA)).thenReturn(3L);
        when(liveStatuses.find(tenantA, 1L)).thenReturn(Optional.of(live(RobotOnlineStatus.ONLINE)));
        when(liveStatuses.find(tenantA, 2L)).thenReturn(Optional.of(live(RobotOnlineStatus.ONLINE)));
        when(liveStatuses.find(tenantA, 3L)).thenReturn(Optional.of(live(RobotOnlineStatus.OFFLINE)));
        when(missions.countCreatedBetween(tenantA, LocalDateTime.of(2026, 9, 16, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0))).thenReturn(4L);
        when(missions.countFailedBetween(tenantA, LocalDateTime.of(2026, 9, 16, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0))).thenReturn(1L);
        when(missions.selectDashboardTrend(tenantA, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 16)))
                .thenReturn(List.of(trend()));

        RobotDashboard dashboard = service.getDashboard(tenantA);

        assertThat(dashboard.totalRobots()).isEqualTo(3);
        assertThat(dashboard.onlineRobots()).isEqualTo(2);
        assertThat(dashboard.todayMissions()).isEqualTo(4);
        assertThat(dashboard.failedMissions()).isEqualTo(1);
        assertThat(dashboard.alarmFeatureEnabled()).isFalse();
        assertThat(dashboard.robots()).extracting(RobotDashboard.RobotStatus::robotCode)
                .doesNotContain(tenantBRobot.getRobotCode());
        verify(robots).selectDashboardRobots(tenantA);
        verify(robots).countDashboardRobots(tenantA);
        verify(missions).countCreatedBetween(tenantA, LocalDateTime.of(2026, 9, 16, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0));
        verify(missions).countFailedBetween(tenantA, LocalDateTime.of(2026, 9, 16, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0));
        verify(missions).selectDashboardTrend(tenantA, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 16));
        verify(robots, never()).selectDashboardRobots(tenantB);
        verify(liveStatuses, never()).find(tenantB, tenantBRobot.getId());
    }

    private static RobotDO robot(long id, long tenantId, String code) {
        return RobotDO.builder().id(id).tenantId(tenantId).robotCode(code).name(code)
                .onlineStatus("OFFLINE").workStatus("IDLE").batteryLevel(50).build();
    }

    private static RobotLiveStatus live(RobotOnlineStatus online) {
        return new RobotLiveStatus(online, RobotWorkStatus.IDLE, 80);
    }

    private static MissionDashboardTrendDO trend() {
        MissionDashboardTrendDO trend = new MissionDashboardTrendDO();
        trend.setMissionDate(LocalDate.of(2026, 9, 16));
        trend.setTotal(4);
        trend.setFailed(1);
        return trend;
    }
}
