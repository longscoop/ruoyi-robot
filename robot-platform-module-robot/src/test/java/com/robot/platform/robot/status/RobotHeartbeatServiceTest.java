package com.robot.platform.robot.status;

import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import com.robot.platform.robot.status.service.RobotHeartbeatServiceImpl;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RobotHeartbeatServiceTest {
    private final RobotMapper robots = mock(RobotMapper.class);
    private final RobotMessageInboxService inbox = mock(RobotMessageInboxService.class);
    private final RobotLiveStatusStore statuses = mock(RobotLiveStatusStore.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_789_041_600_000L), ZoneOffset.UTC);
    private final RobotHeartbeatService service = new RobotHeartbeatServiceImpl(robots, inbox, statuses, clock,
            Duration.ofMinutes(10), Duration.ofMinutes(1));

    @AfterEach void clear() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    @BeforeEach void allowSnapshotPersistence() {
        when(robots.updateHeartbeatSnapshotForLockedRobot(anyLong(), anyLong(), anyString(), anyString(), anyInt(),
                anyString(), nullable(String.class), nullable(String.class), any(LocalDateTime.class))).thenReturn(1);
    }

    @Test
    void acceptsHeartbeatUnderServerDerivedTenantAndWritesTenantScopedStatus() {
        DeviceMqttIdentity device = new DeviceMqttIdentity(4, 10, 7, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot());
        when(inbox.insertIfAbsent(any())).thenReturn(true);

        assertThat(service.accept(device, envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7))).isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);

        InOrder ordered = inOrder(robots, inbox, statuses);
        ordered.verify(robots).selectByTenantAndIdForUpdate(10L, 7L);
        ordered.verify(inbox).insertIfAbsent(any());
        ordered.verify(statuses).put(eq(10L), eq(7L), org.mockito.ArgumentMatchers.<com.robot.platform.robot.status.model.RobotLiveStatus>argThat(status -> status.batteryLevel() == 72
                && status.onlineStatus().name().equals("ONLINE")), eq(Duration.ofMinutes(10)));
        verify(robots).updateHeartbeatSnapshotForLockedRobot(eq(10L), eq(7L), eq("ONLINE"), eq("IDLE"), eq(72),
                eq("2001:db8::1"), eq("M-1"), eq("1.0"), eq(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        assertThat(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void duplicateInboxNeverAppliesBusinessStateAgain() {
        when(inbox.insertIfAbsent(any())).thenReturn(false);
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot());
        DeviceMqttIdentity device = new DeviceMqttIdentity(4, 10, 7, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);

        assertThat(service.accept(device, envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7))).isEqualTo(RobotHeartbeatService.AcceptResult.DUPLICATE);

        verifyNoInteractions(statuses);
    }

    @Test
    void rejectsTelemetryClaimingAnotherRobotBeforeInboxInsert() {
        DeviceMqttIdentity device = new DeviceMqttIdentity(4, 10, 7, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);

        assertThatThrownBy(() -> service.accept(device, envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 8)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(inbox, statuses, robots);
    }

    @Test
    void payloadRejectsOutOfRangeTelemetry() {
        assertThatThrownBy(() -> new HeartbeatPayload(7, 101, 10, 20, 24, "IDLE", "127.0.0.1", null, "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payloadRejectsHostnamesWhereAnIpLiteralIsRequired() {
        assertThatThrownBy(() -> new HeartbeatPayload(7, 70, 10, 20, 24, "IDLE", "localhost", null, "1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ip address");
    }

    @Test
    void unchangedHeartbeatWithinSnapshotIntervalOnlyRefreshesRedis() {
        RobotDO robot = robot();
        robot.setOnlineStatus("ONLINE"); robot.setWorkStatus("IDLE"); robot.setBatteryLevel(72);
        robot.setIpAddress("2001:db8::1"); robot.setCurrentMissionId("M-1"); robot.setSoftwareVersion("1.0");
        robot.setLastHeartbeatTime(LocalDateTime.ofInstant(clock.instant().minusSeconds(30), ZoneOffset.UTC));
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot);
        when(inbox.insertIfAbsent(any())).thenReturn(true);

        service.accept(identity(), envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7));

        verify(robots, never()).updateHeartbeatSnapshotForLockedRobot(anyLong(), anyLong(), anyString(), anyString(),
                anyInt(), anyString(), nullable(String.class), nullable(String.class), any(LocalDateTime.class));
        verify(statuses).put(eq(10L), eq(7L), any(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void batteryOnlyTelemetryRefreshesProjectionButDoesNotEmitLifecycleStatusEvent() {
        RobotDO robot = robot();
        robot.setOnlineStatus("ONLINE"); robot.setWorkStatus("IDLE"); robot.setBatteryLevel(40);
        robot.setIpAddress("2001:db8::1"); robot.setCurrentMissionId("M-1"); robot.setSoftwareVersion("1.0");
        robot.setLastHeartbeatTime(LocalDateTime.ofInstant(clock.instant().minusSeconds(30), ZoneOffset.UTC));
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot);
        when(inbox.insertIfAbsent(any())).thenReturn(true);
        RobotRealtimeEventPublisher events = mock(RobotRealtimeEventPublisher.class);
        RobotHeartbeatService eventAware = new RobotHeartbeatServiceImpl(robots, inbox, statuses, clock,
                Duration.ofMinutes(10), Duration.ofMinutes(1), events);

        eventAware.accept(identity(), envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7));

        verify(statuses).put(eq(10L), eq(7L), argThat(status -> status.batteryLevel() == 72), any());
        verifyNoInteractions(events);
    }

    @Test
    void unchangedHeartbeatAtSnapshotIntervalPersistsRecoverySnapshot() {
        RobotDO robot = robot();
        robot.setOnlineStatus("ONLINE"); robot.setWorkStatus("IDLE"); robot.setBatteryLevel(72);
        robot.setIpAddress("2001:db8::1"); robot.setCurrentMissionId("M-1"); robot.setSoftwareVersion("1.0");
        robot.setLastHeartbeatTime(LocalDateTime.ofInstant(clock.instant().minus(Duration.ofMinutes(1)), ZoneOffset.UTC));
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot);
        when(inbox.insertIfAbsent(any())).thenReturn(true);

        service.accept(identity(), envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7));

        verify(robots).updateHeartbeatSnapshotForLockedRobot(eq(10L), eq(7L), eq("ONLINE"), eq("IDLE"), eq(72),
                eq("2001:db8::1"), eq("M-1"), eq("1.0"), eq(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }

    @Test
    void heartbeatRestoresPreexistingWorkerTenantContext() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(99L);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setIgnore(true);
        when(robots.selectByTenantAndIdForUpdate(10L, 7L)).thenReturn(robot());
        when(inbox.insertIfAbsent(any())).thenReturn(false);

        service.accept(identity(), envelope("01J0A1B2C3D4E5F6G7H8J9K0MN", 7));

        assertThat(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.isIgnore()).isTrue();
    }

    private RobotMessageEnvelope<HeartbeatPayload> envelope(String messageId, long robotId) {
        return new RobotMessageEnvelope<>(messageId, "REQ-1", clock.millis(), 1, MessageType.HEARTBEAT,
                MessageSource.ROBOT, new HeartbeatPayload(robotId, 72, 30, 45, 24, "IDLE", "2001:db8::1", "M-1", "1.0"));
    }
    private static DeviceMqttIdentity identity() {
        return new DeviceMqttIdentity(4, 10, 7, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);
    }
    private static RobotDO robot() { RobotDO robot = new RobotDO(); robot.setId(7L); robot.setTenantId(10L); robot.setDeviceId(4L); return robot; }
}
