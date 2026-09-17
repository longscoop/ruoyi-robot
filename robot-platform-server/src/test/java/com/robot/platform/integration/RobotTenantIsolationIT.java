package com.robot.platform.integration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.service.DeviceAccessPolicy;
import com.robot.platform.device.device.service.DeviceService;
import com.robot.platform.device.device.service.TenantNamespaceResolver;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.device.service.command.DeviceCreateCommand;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.service.RobotService;
import com.robot.platform.tenant.quota.dal.mysql.TenantUsageMapper;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real MySQL proof that RobotDO participates in the tenant interceptor. */
@Import(RobotTenantIsolationIT.Ports.class)
@TestPropertySource(properties = "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RobotTenantIsolationIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private RobotMapper robotMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private DeviceService deviceService;
    @Autowired private RobotService robotService;
    @Autowired private TenantRobotQuotaService quotaService;
    @Autowired private TenantUsageMapper usageMapper;
    @Autowired private DeviceAccessPolicy accessPolicy;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantACannotReadUpdateOrDeleteTenantBRobot() {
        long robotB = fixture(20L, 200L, "B-001");

        TenantUtils.execute(10L, () -> {
            assertThat(robotMapper.selectById(robotB)).isNull();
            assertThat(robotMapper.updateName(robotB, "hacked")).isZero();
            assertThat(robotMapper.deleteById(robotB)).isZero();
        });

        TenantUtils.execute(20L, () -> assertThat(robotMapper.selectById(robotB).getName()).isEqualTo("B-001"));
    }

    @Test
    void deprovisionDoesNotTouchForeignTenantRobot() {
        long robotB = fixture(20L, 200L, "B-002");

        TenantUtils.execute(10L, () -> robotService.deprovision(10L, robotB));

        TenantUtils.execute(20L, () -> assertThat(robotMapper.selectById(robotB)).isNotNull());
    }

    @Test
    void activationCreatesOneRobotAndRollbackLeavesNoRobotOnDuplicateDeviceBinding() {
        ProductDO product = publicProduct();
        platform();
        long deviceId = deviceService.createInventoryDevice(device(product.getId(), "SN-robot"));
        quotaService.updateRobotLimit(10L, 5);

        tenant(10L);
        deviceService.activate(deviceId, activate("A-001"));

        DeviceDO activated = deviceMapper.selectById(deviceId);
        assertThat(activated.getRobotId()).isNotNull();
        assertThat(robotMapper.selectCount(RobotDO::getDeviceId, deviceId)).isEqualTo(1L);
        RobotDO robot = robotMapper.selectById(activated.getRobotId());
        assertThat(robot).extracting(RobotDO::getTenantId, RobotDO::getDeviceId, RobotDO::getRobotCode)
                .containsExactly(10L, deviceId, "A-001");

        // Seed a conflicting binding as an external/concurrent database row. Provisioning must
        // abort the enclosing device activation, so activation state and quota do not drift.
        platform();
        long duplicateDeviceId = deviceService.createInventoryDevice(device(product.getId(), "SN-duplicate"));
        TenantUtils.execute(10L, () -> robotMapper.insert(RobotDO.builder().tenantId(10L).deviceId(duplicateDeviceId)
                .productId(product.getId()).robotCode("A-external").name("external").onlineStatus("OFFLINE")
                .workStatus("IDLE").build()));
        tenant(10L);
        assertThatThrownBy(() -> deviceService.activate(duplicateDeviceId, activate("A-003"))).isInstanceOf(RuntimeException.class);
        TenantUtils.executeIgnore(() -> {
            DeviceDO persisted = deviceMapper.selectById(duplicateDeviceId);
            assertThat(persisted.getTenantId()).isNull();
            assertThat(persisted.getRobotId()).isNull();
            assertThat(persisted.getLifecycleStatus()).isEqualTo("UNACTIVATED");
        });
    }

    @Test
    void unbindClearsInventoryFieldsThenReactivationCreatesNewRobotAndVersion() {
        ProductDO product = publicProduct();
        platform();
        long deviceId = deviceService.createInventoryDevice(device(product.getId(), "SN-rebind"));
        quotaService.updateRobotLimit(10L, 5);

        tenant(10L);
        var first = deviceService.activate(deviceId, activate("A-001"));
        deviceService.unbind(deviceId);

        TenantUtils.executeIgnore(() -> {
            DeviceDO inventory = deviceMapper.selectById(deviceId);
            assertThat(inventory).extracting(DeviceDO::getTenantId, DeviceDO::getRobotId,
                    DeviceDO::getMqttUsername, DeviceDO::getMqttSecretHash, DeviceDO::getHttpSecretCiphertext,
                    DeviceDO::getActivateTime, DeviceDO::getLastBindTime)
                    .containsOnlyNulls();
            assertThat(inventory.getLifecycleStatus()).isEqualTo("UNACTIVATED");
            assertThat(inventory.getCredentialVersion()).isEqualTo(first.credentialVersion() + 1);
        });
        assertThat(robotMapper.selectById(first.robotId())).isNull();
        assertThat(usageMapper.selectByTenantId(10L).getRobotUsed()).isZero();

        tenant(10L);
        var rebound = deviceService.activate(deviceId, activate("A-002"));
        assertThat(rebound.robotId()).isNotEqualTo(first.robotId());
        assertThat(rebound.credentialVersion()).isEqualTo(first.credentialVersion() + 2);
        assertThat(robotMapper.selectCount(RobotDO::getDeviceId, deviceId)).isEqualTo(1L);
        assertThat(usageMapper.selectByTenantId(10L).getRobotUsed()).isEqualTo(1);
    }

    private long fixture(long tenantId, long deviceId, String code) {
        return TenantUtils.execute(tenantId, () -> {
            RobotDO robot = RobotDO.builder().tenantId(tenantId).deviceId(deviceId).productId(3L).robotCode(code)
                    .name(code).onlineStatus("OFFLINE").workStatus("IDLE").build();
            robotMapper.insert(robot);
            return robot.getId();
        });
    }

    private ProductDO publicProduct() {
        ProductDO product = ProductDO.builder().productKey("P-" + System.nanoTime()).name("product").status(0).build();
        productMapper.insert(product);
        return product;
    }

    private void platform() {
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(true);
    }

    private void tenant(long tenantId) {
        TenantContextHolder.setTenantId(tenantId);
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(false);
    }

    private static DeviceCreateCommand device(long productId, String serial) {
        DeviceCreateCommand command = new DeviceCreateCommand();
        command.setProductId(productId);
        command.setDeviceSn(serial);
        command.setName(serial);
        return command;
    }

    private static DeviceActivateCommand activate(String code) {
        DeviceActivateCommand command = new DeviceActivateCommand();
        command.setRobotCode(code);
        command.setRobotName(code);
        return command;
    }

    @TestConfiguration
    @MapperScan(basePackages = {
            "com.robot.platform.device.device.dal.mysql",
            "com.robot.platform.device.group.dal.mysql",
            "com.robot.platform.device.product.dal.mysql",
            "com.robot.platform.tenant.quota.dal.mysql",
            "com.robot.platform.robot.robot.dal.mysql"
    })
    static class Ports {
        @Bean
        @Primary
        DeviceAccessPolicy deviceAccessPolicy() {
            return mock(DeviceAccessPolicy.class);
        }

        @Bean
        @Primary
        TenantNamespaceResolver tenantNamespaceResolver() {
            return tenantId -> "t-" + tenantId;
        }
    }
}
