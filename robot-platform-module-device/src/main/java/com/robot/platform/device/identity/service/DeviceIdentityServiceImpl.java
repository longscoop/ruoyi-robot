package com.robot.platform.device.identity.service;

import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.device.device.enums.DeviceErrorCodeConstants.DEVICE_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class DeviceIdentityServiceImpl implements DeviceIdentityService {
    private final DeviceMapper deviceMapper;
    @Override public DeviceIdentity findBySn(String deviceSn) {
        DeviceDO device = deviceMapper.selectByDeviceSn(deviceSn);
        requireActiveBoundDevice(device);
        return new DeviceIdentity(device.getId(), device.getTenantId(), device.getProductId(), device.getDeviceSn(),
                device.getMqttUsername(), device.getCredentialVersion());
    }
    @Override public DeviceHttpAuthenticationIdentity findForHttpAuthentication(String deviceSn) {
        DeviceDO device = deviceMapper.selectByDeviceSn(deviceSn);
        requireActiveBoundDevice(device);
        return new DeviceHttpAuthenticationIdentity(device.getId(), device.getTenantId(), device.getRobotId(),
                device.getDeviceSn(), device.getCredentialVersion(), device.getHttpSecretCiphertext());
    }
    private static void requireActiveBoundDevice(DeviceDO device) {
        if (device == null || device.getTenantId() == null || device.getRobotId() == null
                || device.getHttpSecretCiphertext() == null || !DeviceLifecycle.ACTIVATED.name().equals(device.getLifecycleStatus())) {
            throw exception(DEVICE_NOT_EXISTS);
        }
    }
}
