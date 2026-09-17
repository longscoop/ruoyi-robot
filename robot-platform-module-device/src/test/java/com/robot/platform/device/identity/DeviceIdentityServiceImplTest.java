package com.robot.platform.device.identity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.identity.service.DeviceIdentityServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceIdentityServiceImplTest {
    private final DeviceMapper mapper = mock(DeviceMapper.class);
    private final DeviceIdentityServiceImpl service = new DeviceIdentityServiceImpl(mapper);

    @Test
    void rejectsDisabledDeviceForHttpAuthentication() {
        when(mapper.selectByDeviceSn("SN-disabled")).thenReturn(device(DeviceLifecycle.DISABLED.name(), 10L, 9L));

        assertThatThrownBy(() -> service.findForHttpAuthentication("SN-disabled"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsUnboundDeviceForHttpAuthentication() {
        when(mapper.selectByDeviceSn("SN-unbound")).thenReturn(device(DeviceLifecycle.ACTIVATED.name(), null, null));

        assertThatThrownBy(() -> service.findForHttpAuthentication("SN-unbound"))
                .isInstanceOf(ServiceException.class);
    }

    private static DeviceDO device(String lifecycle, Long tenantId, Long robotId) {
        return DeviceDO.builder().id(7L).tenantId(tenantId).robotId(robotId).deviceSn("SN-test")
                .credentialVersion(4).lifecycleStatus(lifecycle).httpSecretCiphertext("ciphertext").build();
    }
}
