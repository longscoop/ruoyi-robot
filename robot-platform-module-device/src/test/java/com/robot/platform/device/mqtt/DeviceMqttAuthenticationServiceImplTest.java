package com.robot.platform.device.mqtt;

import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.TenantNamespaceResolver;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationServiceImpl;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.device.product.enums.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeviceMqttAuthenticationServiceImplTest {
    private final DeviceMapper devices = mock(DeviceMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final TenantNamespaceResolver namespaces = mock(TenantNamespaceResolver.class);
    private final DeviceMqttAuthenticationServiceImpl service = new DeviceMqttAuthenticationServiceImpl(devices, products, namespaces);

    @Test
    void resolvesOnlyAnActiveBoundDeviceWhosePersistedUsernameMatchesEveryServerDimension() {
        DeviceDO device = device(DeviceLifecycle.ACTIVATED.name(), 9L);
        when(devices.selectByMqttUsername("tenant-a/product-x/SN-1")).thenReturn(device);
        when(products.selectById(3L)).thenReturn(product());
        when(namespaces.resolve(10L)).thenReturn("tenant-a");

        assertThat(service.findActiveByUsername("tenant-a/product-x/SN-1")).hasValueSatisfying(identity ->
                assertThat(identity).extracting("deviceId", "tenantId", "robotId", "mqttSecretHash")
                        .containsExactly(7L, 10L, 9L, "password-hash"));
    }

    @Test
    void deniesUnknownDisabledAndUnboundDevicesBeforeCredentialVerification() {
        assertThat(service.findActiveByUsername("tenant-a/product-x/UNKNOWN")).isEmpty();

        when(devices.selectByMqttUsername("tenant-a/product-x/SN-1"))
                .thenReturn(device(DeviceLifecycle.DISABLED.name(), 9L), device(DeviceLifecycle.ACTIVATED.name(), null));

        assertThat(service.findActiveByUsername("tenant-a/product-x/SN-1")).isEmpty();
        assertThat(service.findActiveByUsername("tenant-a/product-x/SN-1")).isEmpty();
        verifyNoInteractions(products, namespaces);
    }

    private static DeviceDO device(String lifecycle, Long robotId) {
        return DeviceDO.builder().id(7L).tenantId(10L).productId(3L).robotId(robotId).deviceSn("SN-1")
                .mqttUsername("tenant-a/product-x/SN-1").mqttSecretHash("password-hash")
                .credentialVersion(4).lifecycleStatus(lifecycle).build();
    }
    private static ProductDO product() {
        return ProductDO.builder().id(3L).productKey("product-x").status(ProductStatus.ENABLE.getStatus()).build();
    }
}
