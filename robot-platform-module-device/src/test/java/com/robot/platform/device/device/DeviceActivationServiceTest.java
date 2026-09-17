package com.robot.platform.device.device;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.*;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.service.ProductService;
import com.robot.platform.device.spi.RobotProvisioningGateway;
import com.robot.platform.security.crypto.AesGcmSecretCipher;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceActivationServiceTest {
    private final DeviceMapper mapper = mock(DeviceMapper.class);
    private final ProductService products = mock(ProductService.class);
    private final TenantRobotQuotaService quota = mock(TenantRobotQuotaService.class);
    private final RobotProvisioningGateway gateway = mock(RobotProvisioningGateway.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final DeviceAccessPolicy access = mock(DeviceAccessPolicy.class);
    private final TenantNamespaceResolver namespace = mock(TenantNamespaceResolver.class);
    private final DeviceCredentialRevocationPublisher revocations = mock(DeviceCredentialRevocationPublisher.class);
    private final DeviceService service = new DeviceServiceImpl(mapper, products, quota, gateway,
            new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[32])), encoder, access, namespace, revocations);

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void activationReturnsDistinctOneTimeSecretsAndStoresOnlyProtectedForms() {
        TenantContextHolder.setTenantId(10L);
        DeviceDO device = inventory();
        when(mapper.selectByIdForUpdate(7L)).thenReturn(device);
        when(products.requireActivatable(3L, 10L)).thenReturn(ProductDO.builder().id(3L).productKey("P-1").status(0).build());
        when(namespace.resolve(10L)).thenReturn("t-10");
        when(encoder.encode(anyString())).thenReturn("bcrypt-verifier");
        when(gateway.provision(any())).thenReturn(9L);

        DeviceActivationResult result = service.activate(7L, activation());

        assertThat(result.mqttSecret()).isNotBlank();
        assertThat(result.httpSecret()).isNotBlank().isNotEqualTo(result.mqttSecret());
        assertThat(result.mqttUsername()).isEqualTo("t-10/P-1/SN-1");
        assertThat(device.getMqttSecretHash()).doesNotContain(result.mqttSecret());
        assertThat(device.getHttpSecretCiphertext()).doesNotContain(result.httpSecret());
        assertThat(result.toString()).doesNotContain(result.mqttSecret()).doesNotContain(result.httpSecret());
        assertThat(device.getTenantId()).isEqualTo(10L);
        verify(quota).checkCanActivate(10L); verify(quota).changeRobotUsage(10L, 1);
    }

    @Test
    void foreignOrDisabledProductNeverProvisionsRobot() {
        TenantContextHolder.setTenantId(10L);
        when(mapper.selectByIdForUpdate(7L)).thenReturn(inventory());
        doThrow(new ServiceException(1, "产品型号状态不合法")).when(products).requireActivatable(3L, 10L);
        assertThatThrownBy(() -> service.activate(7L, activation())).isInstanceOf(ServiceException.class);
        verifyNoInteractions(gateway); verify(quota, never()).changeRobotUsage(anyLong(), anyInt());
    }

    @Test
    void provisioningFailureRollsBackBeforeQuotaUsageChange() {
        TenantContextHolder.setTenantId(10L);
        when(mapper.selectByIdForUpdate(7L)).thenReturn(inventory());
        when(products.requireActivatable(3L, 10L)).thenReturn(ProductDO.builder().id(3L).productKey("P-1").status(0).build());
        when(gateway.provision(any())).thenThrow(new IllegalStateException("robot unavailable"));
        assertThatThrownBy(() -> service.activate(7L, activation())).isInstanceOf(IllegalStateException.class);
        verify(quota, never()).changeRobotUsage(anyLong(), anyInt());
    }
    @Test
    void serverDerivedNamespaceAndCredentialVersionRemainMonotonicAcrossUnbindReactivateAndRotate() {
        TenantContextHolder.setTenantId(10L);
        DeviceDO device = inventory(); device.setCredentialVersion(6);
        when(mapper.selectByIdForUpdate(7L)).thenReturn(device);
        when(products.requireActivatable(3L, 10L)).thenReturn(ProductDO.builder().id(3L).productKey("P-1").status(0).build());
        when(namespace.resolve(10L)).thenReturn("t-10"); when(encoder.encode(anyString())).thenReturn("verifier");
        when(gateway.provision(any())).thenReturn(9L, 10L);
        when(mapper.resetToInventoryAfterUnbind(7L, 10L, 7, 8)).thenReturn(1);

        DeviceActivationResult first = service.activate(7L, activation());
        service.unbind(7L);
        DeviceActivationResult second = service.activate(7L, activation());
        DeviceActivationResult rotated = service.rotateCredentials(7L);

        assertThat(first.mqttUsername()).startsWith("t-10/");
        assertThat(first.credentialVersion()).isEqualTo(7);
        assertThat(second.credentialVersion()).isEqualTo(9);
        assertThat(rotated.credentialVersion()).isEqualTo(10);
        verify(namespace, times(2)).resolve(10L);
        verify(revocations, times(2)).publish(any(DeviceCredentialsRevokedEvent.class));
    }
    @Test void unbindPersistsEveryNullableBindingFieldWithVersionGuard() {
        TenantContextHolder.setTenantId(10L);
        DeviceDO device=DeviceDO.builder().id(7L).tenantId(10L).productId(3L).robotId(9L).deviceSn("SN-1").name("d")
                .lifecycleStatus(DeviceLifecycle.ACTIVATED.name()).credentialVersion(4).mqttUsername("t-10/P-1/SN-1")
                .mqttSecretHash("hash").httpSecretCiphertext("cipher").build();
        when(mapper.selectByIdForUpdate(7L)).thenReturn(device);
        when(mapper.resetToInventoryAfterUnbind(7L,10L,4,5)).thenReturn(1);

        service.unbind(7L);

        verify(mapper).resetToInventoryAfterUnbind(7L,10L,4,5);
        assertThat(device).extracting(DeviceDO::getTenantId,DeviceDO::getRobotId,DeviceDO::getMqttUsername,
                DeviceDO::getMqttSecretHash,DeviceDO::getHttpSecretCiphertext,DeviceDO::getActivateTime,DeviceDO::getLastBindTime)
                .containsOnlyNulls();
        assertThat(device.getLifecycleStatus()).isEqualTo(DeviceLifecycle.UNACTIVATED.name());
        assertThat(device.getCredentialVersion()).isEqualTo(5);
    }
    @Test void disablingInvalidatesSessionsButRetainsLongTermCredentialsForReenable() {
        TenantContextHolder.setTenantId(10L);
        DeviceDO device=DeviceDO.builder().id(7L).tenantId(10L).productId(3L).robotId(9L).deviceSn("SN-1").name("d")
                .lifecycleStatus(DeviceLifecycle.ACTIVATED.name()).credentialVersion(4).mqttUsername("t-10/P-1/SN-1")
                .mqttSecretHash("hash").httpSecretCiphertext("cipher").build();
        when(mapper.selectByIdForUpdate(7L)).thenReturn(device);
        service.changeLifecycle(7L,DeviceLifecycle.DISABLED);
        assertThat(device.getCredentialVersion()).isEqualTo(5);
        assertThat(device.getMqttUsername()).isEqualTo("t-10/P-1/SN-1");
        assertThat(device.getMqttSecretHash()).isEqualTo("hash");
        assertThat(device.getHttpSecretCiphertext()).isEqualTo("cipher");
        verify(revocations).publish(argThat(event -> event.reason().equals("DISABLED")));
    }
    private static DeviceDO inventory() { return DeviceDO.builder().id(7L).productId(3L).deviceSn("SN-1").name("device").lifecycleStatus(DeviceLifecycle.UNACTIVATED.name()).credentialVersion(0).build(); }
    private static DeviceActivateCommand activation() { DeviceActivateCommand c=new DeviceActivateCommand(); c.setRobotCode("R-001"); c.setRobotName("robot"); return c; }
}
