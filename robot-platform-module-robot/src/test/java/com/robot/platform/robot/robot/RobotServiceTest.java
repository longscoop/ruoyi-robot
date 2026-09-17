package com.robot.platform.robot.robot.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.robot.robot.dal.dataobject.RobotCapabilityDO;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotCapabilityMapper;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RobotServiceTest {

    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final RobotCapabilityMapper capabilityMapper = mock(RobotCapabilityMapper.class);
    private final RobotServiceImpl service = new RobotServiceImpl(robotMapper, capabilityMapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void provisioningKeepsOnlineAndWorkStatusIndependent() {
        // A regression would conflate connection health with the robot's work state.
        TenantContextHolder.setTenantId(10L);
        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            invocation.<RobotDO>getArgument(0).setId(ids.incrementAndGet());
            return 1;
        }).when(robotMapper).insert((RobotDO) any());

        long id = service.provision(new RobotProvisionCommand(10L, 100L, 3L, "R-001", "客厅机器人"));

        verify(robotMapper).insert(org.mockito.ArgumentMatchers.<RobotDO>argThat(robot -> robot.getId().equals(id)
                && robot.getTenantId().equals(10L)
                && robot.getOnlineStatus().equals(RobotOnlineStatus.OFFLINE.name())
                && robot.getWorkStatus().equals(RobotWorkStatus.IDLE.name())));
    }

    @Test
    void liveStatusAcceptsOnlyPhysicalBatteryRange() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectById(7L)).thenReturn(robot(7L, 10L, 100L, "R-001"));

        service.applyLiveStatus(7L, new RobotLiveStatus(RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE, 0));
        service.applyLiveStatus(7L, new RobotLiveStatus(RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE, 100));
        assertThatThrownBy(() -> service.applyLiveStatus(7L,
                new RobotLiveStatus(RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE, -1))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.applyLiveStatus(7L,
                new RobotLiveStatus(RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE, 101))).isInstanceOf(ServiceException.class);

        verify(robotMapper, times(2)).updateById((RobotDO) any());
    }

    @Test
    void duplicateRobotCodeIsReportedWithinTenant() {
        TenantContextHolder.setTenantId(10L);
        doThrow(new DuplicateKeyException("uk_robot_tenant_code"))
                .when(robotMapper).insert((RobotDO) any());

        assertThatThrownBy(() -> service.provision(new RobotProvisionCommand(10L, 100L, 3L, "R-001", "客厅机器人")))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void equivalentProvisionRetryReturnsExistingRobotId() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectByDeviceId(100L)).thenReturn(robot(7L, 10L, 100L, "R-001"));

        assertThat(service.provision(new RobotProvisionCommand(10L, 100L, 3L, "R-001", "robot"))).isEqualTo(7L);

        verify(robotMapper, never()).insert((RobotDO) any());
    }

    @Test
    void conflictingProvisionRetryIsRejected() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectByDeviceId(100L)).thenReturn(robot(7L, 10L, 100L, "R-001"));

        assertThatThrownBy(() -> service.provision(new RobotProvisionCommand(10L, 100L, 3L, "R-other", "robot")))
                .isInstanceOf(ServiceException.class);

        verify(robotMapper, never()).insert((RobotDO) any());
    }

    @Test
    void concurrentEquivalentProvisionRetryReturnsWinner() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectByDeviceId(100L)).thenReturn(null, robot(8L, 10L, 100L, "R-001"));
        doThrow(new DuplicateKeyException("uk_robot_device")).when(robotMapper).insert((RobotDO) any());

        assertThat(service.provision(new RobotProvisionCommand(10L, 100L, 3L, "R-001", "robot"))).isEqualTo(8L);
    }

    @Test
    void repeatedDeprovisionOfAbsentRobotIsNoOp() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectById(7L)).thenReturn(null);

        service.deprovision(10L, 7L);

        verifyNoInteractions(capabilityMapper);
        verify(robotMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void directDeletionRefusesDeviceBackedRobot() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectById(7L)).thenReturn(robot(7L, 10L, 100L, "R-001"));

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(ServiceException.class);

        verify(robotMapper, never()).deleteById(anyLong());
    }

    @Test
    void capabilityUpsertAndListStayWithinCurrentTenantRobot() {
        TenantContextHolder.setTenantId(10L);
        when(robotMapper.selectById(7L)).thenReturn(robot(7L, 10L, 100L, "R-001"));
        RobotCapabilityService capabilities = service.capabilities();
        doAnswer(invocation -> {
            invocation.<RobotCapabilityDO>getArgument(0).setId(11L);
            return 1;
        }).when(capabilityMapper).insert((RobotCapabilityDO) any());
        RobotCapabilityDO persisted = RobotCapabilityDO.builder().id(11L).tenantId(10L)
                .robotId(7L).capabilityCode("navigation").configuration("{\"mode\":\"safe\"}").build();
        when(capabilityMapper.selectByRobotId(7L)).thenReturn(List.of(persisted));

        capabilities.upsert(7L, "navigation", "{\"mode\":\"safe\"}");

        assertThat(capabilities.list(7L)).containsExactly(persisted);
        verify(capabilityMapper).insert(org.mockito.ArgumentMatchers.<RobotCapabilityDO>argThat(capability -> capability.getTenantId().equals(10L)
                && capability.getRobotId().equals(7L)
                && capability.getCapabilityCode().equals("navigation")));
    }

    @Test
    void capabilityRejectsAnotherTenantRobot() {
        TenantContextHolder.setTenantId(10L);
        // A tenant-intercepted mapper exposes foreign rows as absent.
        when(robotMapper.selectById(8L)).thenReturn(null);

        assertThatThrownBy(() -> service.capabilities().upsert(8L, "navigation", "{}"))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(capabilityMapper);
    }

    private static RobotDO robot(long id, long tenantId, long deviceId, String robotCode) {
        return RobotDO.builder().id(id).tenantId(tenantId).deviceId(deviceId).productId(3L)
                .robotCode(robotCode).name("robot").onlineStatus(RobotOnlineStatus.OFFLINE.name())
                .workStatus(RobotWorkStatus.IDLE.name()).batteryLevel(50).build();
    }
}
