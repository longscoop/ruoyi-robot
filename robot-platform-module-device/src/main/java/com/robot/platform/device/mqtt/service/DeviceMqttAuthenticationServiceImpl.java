package com.robot.platform.device.mqtt.service;

import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.TenantNamespaceResolver;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.device.product.enums.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The only callback identity lookup. Both the username format and all binding dimensions are
 * re-derived from database records, preventing a callback body from choosing a tenant or device.
 */
@Service
@RequiredArgsConstructor
public class DeviceMqttAuthenticationServiceImpl implements DeviceMqttAuthenticationService {
    private final DeviceMapper deviceMapper;
    private final ProductMapper productMapper;
    private final TenantNamespaceResolver namespaces;

    @Override public Optional<DeviceMqttIdentity> findActiveByUsername(String mqttUsername) {
        if (!validUsername(mqttUsername)) return Optional.empty();
        DeviceDO device = deviceMapper.selectByMqttUsername(mqttUsername);
        if (device == null || device.getTenantId() == null || device.getProductId() == null || device.getRobotId() == null
                || !DeviceLifecycle.ACTIVATED.name().equals(device.getLifecycleStatus()) || blank(device.getMqttSecretHash())) {
            return Optional.empty();
        }
        ProductDO product = productMapper.selectById(device.getProductId());
        if (product == null || !Integer.valueOf(ProductStatus.ENABLE.getStatus()).equals(product.getStatus())
                || (product.getTenantId() != null && !product.getTenantId().equals(device.getTenantId()))) return Optional.empty();
        String namespace;
        try { namespace = namespaces.resolve(device.getTenantId()); } catch (RuntimeException ignored) { return Optional.empty(); }
        String expected = namespace + "/" + product.getProductKey() + "/" + device.getDeviceSn();
        if (!expected.equals(mqttUsername) || !expected.equals(device.getMqttUsername())) return Optional.empty();
        return Optional.of(new DeviceMqttIdentity(device.getId(), device.getTenantId(), device.getRobotId(), namespace,
                product.getProductKey(), device.getDeviceSn(), expected, device.getMqttSecretHash(), device.getCredentialVersion()));
    }

    private static boolean validUsername(String username) {
        if (username == null || username.length() > 384 || username.indexOf('+') >= 0 || username.indexOf('#') >= 0) return false;
        String[] parts = username.split("/", -1);
        return parts.length == 3 && java.util.Arrays.stream(parts).allMatch(part -> part.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
