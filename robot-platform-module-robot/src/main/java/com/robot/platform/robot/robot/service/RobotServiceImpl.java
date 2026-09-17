package com.robot.platform.robot.robot.service;

import com.robot.platform.framework.common.pojo.PageResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.robot.robot.convert.RobotConvert;
import com.robot.platform.robot.robot.dal.dataobject.RobotCapabilityDO;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotCapabilityMapper;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.robot.service.command.RobotPageQuery;
import com.robot.platform.robot.robot.service.command.RobotUpdateCommand;
import com.robot.platform.robot.robot.service.dto.RobotRespDTO;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.robot.robot.enums.RobotErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class RobotServiceImpl implements RobotService, RobotCapabilityService {
    private final RobotMapper robotMapper;
    private final RobotCapabilityMapper capabilityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long provision(RobotProvisionCommand command) {
        requireCommandTenant(command.tenantId());
        RobotDO existing = robotMapper.selectByDeviceId(command.deviceId());
        if (existing != null) {
            return existingIdForEquivalentCommand(existing, command);
        }
        RobotDO robot = RobotDO.builder().tenantId(command.tenantId()).deviceId(command.deviceId())
                .productId(command.productId()).robotCode(command.robotCode()).name(command.robotName())
                // Connection and work state must start independently; provisioning has no telemetry.
                .onlineStatus(RobotOnlineStatus.OFFLINE.name()).workStatus(RobotWorkStatus.IDLE.name())
                .batteryLevel(null).build();
        try {
            robotMapper.insert(robot);
        } catch (DuplicateKeyException exception) {
            // The database arbitrates concurrent provisioning. A retry is safe only if the
            // winner has the exact same tenant/device/product/code/name binding.
            RobotDO winner = robotMapper.selectByDeviceId(command.deviceId());
            if (winner != null) {
                return existingIdForEquivalentCommand(winner, command);
            }
            throw exception(ROBOT_CODE_DUPLICATE);
        }
        return robot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deprovision(long tenantId, long robotId) {
        requireCommandTenant(tenantId);
        RobotDO robot = robotMapper.selectById(robotId);
        if (robot == null) {
            // An earlier unbind may already have physically removed this tenant-scoped row.
            return;
        }
        if (robot.getDeviceId() == null) {
            throw exception(ROBOT_DELETE_REQUIRES_DEPROVISION);
        }
        // This is intentionally the sole deletion path for device-backed rows. Physical deletion
        // is required because device_id is globally unique and activation may happen again later.
        capabilityMapper.physicalDeleteByRobotId(robotId);
        if (robotMapper.physicalDeleteById(robotId) == 0) {
            throw exception(ROBOT_NOT_EXISTS);
        }
    }

    @Override
    public RobotRespDTO get(long id) {
        return RobotConvert.INSTANCE.convert(requireRobot(id));
    }

    @Override
    public PageResult<RobotRespDTO> page(RobotPageQuery query) {
        PageResult<RobotDO> page = robotMapper.selectPage(query);
        return new PageResult<>(page.getList().stream().map(RobotConvert.INSTANCE::convert).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long id, RobotUpdateCommand command) {
        RobotDO robot = requireRobot(id);
        robot.setName(command.getName());
        robotMapper.updateById(robot);
    }

    /**
     * Internal telemetry hook reserved for Task 9. Its package-private boundary prevents
     * management/admin callers from manufacturing live robot state.
     */
    @Transactional(rollbackFor = Exception.class)
    void applyLiveStatus(long id, RobotLiveStatus status) {
        if (status.batteryLevel() != null && (status.batteryLevel() < 0 || status.batteryLevel() > 100)) {
            throw exception(ROBOT_BATTERY_INVALID);
        }
        RobotDO robot = requireRobot(id);
        robot.setOnlineStatus(status.onlineStatus().name());
        robot.setWorkStatus(status.workStatus().name());
        robot.setBatteryLevel(status.batteryLevel());
        robotMapper.updateById(robot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        RobotDO robot = requireRobot(id);
        // Admin CRUD cannot sever the device's binding behind DeviceService's back.
        if (robot.getDeviceId() != null) {
            throw exception(ROBOT_DELETE_REQUIRES_DEPROVISION);
        }
        robotMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(long robotId, String capabilityCode, String configuration) {
        RobotDO robot = requireRobot(robotId);
        RobotCapabilityDO capability = capabilityMapper.selectByRobotIdAndCapabilityCode(robotId, capabilityCode);
        if (capability == null) {
            capability = RobotCapabilityDO.builder().tenantId(robot.getTenantId()).robotId(robotId)
                    .capabilityCode(capabilityCode).configuration(configuration).build();
            try {
                capabilityMapper.insert(capability);
            } catch (DuplicateKeyException exception) {
                throw exception(ROBOT_CAPABILITY_DUPLICATE);
            }
            return;
        }
        capability.setConfiguration(configuration);
        capabilityMapper.updateById(capability);
    }

    @Override
    public List<RobotCapabilityDO> list(long robotId) {
        requireRobot(robotId);
        return capabilityMapper.selectByRobotId(robotId);
    }

    /** Kept for compact service tests; Spring also exposes this instance by the capability interface. */
    public RobotCapabilityService capabilities() {
        return this;
    }

    private RobotDO requireRobot(long id) {
        RobotDO robot = robotMapper.selectById(id);
        if (robot == null) {
            // Tenant interception makes a foreign row indistinguishable from a missing one.
            throw exception(ROBOT_NOT_EXISTS);
        }
        return robot;
    }

    private static long existingIdForEquivalentCommand(RobotDO existing, RobotProvisionCommand command) {
        if (existing.getTenantId().equals(command.tenantId())
                && existing.getDeviceId().equals(command.deviceId())
                && existing.getProductId().equals(command.productId())
                && existing.getRobotCode().equals(command.robotCode())
                && existing.getName().equals(command.robotName())) {
            return existing.getId();
        }
        throw exception(ROBOT_DEVICE_ALREADY_PROVISIONED);
    }

    private static void requireCommandTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw exception(ROBOT_TENANT_MISMATCH);
        }
    }
}
