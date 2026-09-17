package com.robot.platform.integration;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.*;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.device.service.command.DeviceCreateCommand;
import com.robot.platform.device.device.service.command.DeviceInventoryUpdateCommand;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.device.product.enums.ProductStatus;
import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.device.spi.RobotProvisioningGateway;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

/** Real transactional DeviceService + MySQL test; only boundary ports are test doubles. */
@Import(DeviceTenantIsolationIT.Ports.class)
@TestPropertySource(properties = "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class DeviceTenantIsolationIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private DeviceService deviceService;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private TenantRobotQuotaService quotaService;
    @Autowired private TenantUsageMapper usageMapper;
    @Autowired private DeviceAccessPolicy accessPolicy;
    @Autowired private Gateway gateway;

    @BeforeEach void resetPorts() { reset(accessPolicy); gateway.fail.set(false); gateway.ids.set(1000); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void platformAdminPerformsInventoryCrudAgainstRealRows() {
        platform(); ProductDO product=publicProduct();
        long id=deviceService.createInventoryDevice(create(product.getId(),"SN-I"));
        assertThat(deviceMapper.selectById(id)).extracting(DeviceDO::getTenantId,DeviceDO::getLifecycleStatus).containsExactly(null,"UNACTIVATED");
        DeviceInventoryUpdateCommand update=new DeviceInventoryUpdateCommand(); update.setProductId(product.getId()); update.setDeviceSn("SN-I-2"); update.setName("renamed");
        deviceService.updateInventoryDevice(id,update);
        assertThat(deviceService.listInventory()).extracting(DeviceDO::getId).contains(id);
        assertThat(deviceService.get(id).getDeviceSn()).isEqualTo("SN-I-2");
        deviceService.deleteInventoryDevice(id);
        assertThat(deviceMapper.selectById(id)).isNull();
    }

    @Test void tenantACannotChangeTenantBAndPersistedStateRemainsUnchanged() {
        ProductDO product=publicProduct(); long id=activateFor(202L,product);
        DeviceDO before=deviceMapper.selectById(id); TenantContextHolder.setTenantId(101L); tenant();
        assertSensitiveStatePresent(before);
        assertThatThrownBy(() -> deviceService.get(id)).isInstanceOf(ServiceException.class);
        assertPersistedStateUnchanged(id,before);
        assertThatThrownBy(() -> deviceService.updateInventoryDevice(id,update(product.getId(),"bad"))).isInstanceOf(ServiceException.class);
        assertPersistedStateUnchanged(id,before);
        assertThatThrownBy(() -> deviceService.changeLifecycle(id,DeviceLifecycle.DISABLED)).isInstanceOf(ServiceException.class);
        assertPersistedStateUnchanged(id,before);
        assertThatThrownBy(() -> deviceService.rotateCredentials(id)).isInstanceOf(ServiceException.class);
        assertPersistedStateUnchanged(id,before);
        assertThatThrownBy(() -> deviceService.unbind(id)).isInstanceOf(ServiceException.class);
        assertPersistedStateUnchanged(id,before);
    }

    @Test void provisioningFailureRollsBackPersistedActivationAndQuota() {
        ProductDO product=publicProduct(); platform(); long id=deviceService.createInventoryDevice(create(product.getId(),"SN-R"));
        DeviceDO before=deviceMapper.selectById(id);
        assertSensitiveStateAbsent(before);
        quotaService.updateRobotLimit(101L,5); gateway.fail.set(true); TenantContextHolder.setTenantId(101L); tenant();
        assertThatThrownBy(() -> deviceService.activate(id,activate())).isInstanceOf(IllegalStateException.class);
        DeviceDO persisted=deviceMapper.selectById(id);
        assertThat(persisted.getTenantId()).isNull(); assertThat(persisted.getLifecycleStatus()).isEqualTo("UNACTIVATED");
        assertThat(persisted.getCredentialVersion()).isZero(); assertThat(persisted.getRobotId()).isNull();
        assertSensitiveStateAbsent(persisted);
        assertPersistedStateUnchanged(id,before);
        assertThat(usageMapper.selectByTenantId(101L).getRobotUsed()).isZero();
    }

    @Test void ownerCanActivateOnceButCannotProvisionSameDeviceTwice() {
        ProductDO product=publicProduct(); long id=activateFor(101L,product);
        Long robotId=deviceMapper.selectById(id).getRobotId();
        assertThat(robotId).isNotNull();
        assertThatThrownBy(() -> deviceService.activate(id,activate())).isInstanceOf(ServiceException.class);
        assertThat(deviceMapper.selectById(id).getRobotId()).isEqualTo(robotId);
    }

    private void assertPersistedStateUnchanged(long id,DeviceDO before) {
        DeviceDO after=deviceMapper.selectById(id);
        assertThat(after).isNotNull();
        assertSoftly(softly -> {
            softly.assertThat(after.getTenantId()).as("tenant id").isEqualTo(before.getTenantId());
            softly.assertThat(after.getLifecycleStatus()).as("lifecycle").isEqualTo(before.getLifecycleStatus());
            softly.assertThat(after.getCredentialVersion()).as("credential version").isEqualTo(before.getCredentialVersion());
            softly.assertThat(after.getRobotId()).as("robot id").isEqualTo(before.getRobotId());
            // Compare secret-bearing fields as booleans so failure output cannot disclose values.
            softly.assertThat(Objects.equals(after.getMqttUsername(),before.getMqttUsername())).as("MQTT username unchanged").isTrue();
            softly.assertThat(Objects.equals(after.getMqttSecretHash(),before.getMqttSecretHash())).as("MQTT secret hash unchanged").isTrue();
            softly.assertThat(Objects.equals(after.getHttpSecretCiphertext(),before.getHttpSecretCiphertext())).as("HTTP secret ciphertext unchanged").isTrue();
            softly.assertThat(after.getActivateTime()).as("activation time").isEqualTo(before.getActivateTime());
            softly.assertThat(after.getLastBindTime()).as("last bind time").isEqualTo(before.getLastBindTime());
        });
    }

    private static void assertSensitiveStateAbsent(DeviceDO device) {
        assertSoftly(softly -> {
            softly.assertThat(device.getMqttUsername() == null).as("MQTT username absent").isTrue();
            softly.assertThat(device.getMqttSecretHash() == null).as("MQTT secret hash absent").isTrue();
            softly.assertThat(device.getHttpSecretCiphertext() == null).as("HTTP secret ciphertext absent").isTrue();
            softly.assertThat(device.getActivateTime()).as("activation time absent").isNull();
            softly.assertThat(device.getLastBindTime()).as("last bind time absent").isNull();
        });
    }

    private static void assertSensitiveStatePresent(DeviceDO device) {
        assertSoftly(softly -> {
            softly.assertThat(device.getMqttUsername() != null).as("MQTT username present").isTrue();
            softly.assertThat(device.getMqttSecretHash() != null).as("MQTT secret hash present").isTrue();
            softly.assertThat(device.getHttpSecretCiphertext() != null).as("HTTP secret ciphertext present").isTrue();
            softly.assertThat(device.getActivateTime()).as("activation time present").isNotNull();
            softly.assertThat(device.getLastBindTime()).as("last bind time present").isNotNull();
        });
    }

    private long activateFor(long tenantId,ProductDO product) { platform(); long id=deviceService.createInventoryDevice(create(product.getId(),"SN-"+tenantId)); quotaService.updateRobotLimit(tenantId,5); TenantContextHolder.setTenantId(tenantId); tenant(); deviceService.activate(id,activate()); return id; }
    private ProductDO publicProduct() { ProductDO p=ProductDO.builder().productKey("P-"+System.nanoTime()).name("p").status(ProductStatus.ENABLE.getStatus()).build(); productMapper.insert(p); return p; }
    private void platform() { when(accessPolicy.isPlatformSuperAdmin()).thenReturn(true); }
    private void tenant() { when(accessPolicy.isPlatformSuperAdmin()).thenReturn(false); }
    private static DeviceCreateCommand create(long product,String sn) { DeviceCreateCommand c=new DeviceCreateCommand(); c.setProductId(product);c.setDeviceSn(sn);c.setName(sn);return c; }
    private static DeviceInventoryUpdateCommand update(long product,String sn) { DeviceInventoryUpdateCommand c=new DeviceInventoryUpdateCommand(); c.setProductId(product);c.setDeviceSn(sn);c.setName(sn);return c; }
    private static DeviceActivateCommand activate() { DeviceActivateCommand c=new DeviceActivateCommand();c.setRobotCode("R");c.setRobotName("robot");return c; }

    @TestConfiguration
    @MapperScan(basePackages = {
            "com.robot.platform.device.device.dal.mysql",
            "com.robot.platform.device.group.dal.mysql",
            "com.robot.platform.device.product.dal.mysql",
            "com.robot.platform.tenant.quota.dal.mysql"
    })
    static class Ports {
        @Bean @Primary DeviceAccessPolicy deviceAccessPolicy() { return mock(DeviceAccessPolicy.class); }
        @Bean @Primary TenantNamespaceResolver tenantNamespaceResolver() { return id -> "t-"+id; }
        @Bean @Primary DeviceCredentialRevocationPublisher deviceCredentialRevocationPublisher() { return event -> { }; }
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean @Primary RobotProvisioningGateway robotProvisioningGateway(Gateway gateway) { return gateway; }
    }
    static final class Gateway implements RobotProvisioningGateway {
        final AtomicBoolean fail=new AtomicBoolean(); final AtomicLong ids=new AtomicLong();
        @Override public long provision(RobotProvisionCommand command) { if(fail.get()) throw new IllegalStateException("test provisioning failure"); return ids.incrementAndGet(); }
        @Override public void deprovision(long tenantId,long robotId) { }
    }
}
