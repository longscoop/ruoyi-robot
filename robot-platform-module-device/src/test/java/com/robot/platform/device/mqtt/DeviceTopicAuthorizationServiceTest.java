package com.robot.platform.device.mqtt;

import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.device.mqtt.service.DeviceTopicAuthorizationService;
import com.robot.platform.mqtt.MqttAuthorizationAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTopicAuthorizationServiceTest {
    private final DeviceTopicAuthorizationService service = new DeviceTopicAuthorizationService();
    private final DeviceMqttIdentity device = new DeviceMqttIdentity(7L, 10L, 9L, "tenant-a", "product-x", "SN-1",
            "tenant-a/product-x/SN-1", "password-hash", 1);

    @Test
    void permitsOnlyExactDirectionSpecificTopicsWithQosOneAndNoRetain() {
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/SN-1/command", 1, false)).isTrue();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/SN-1/ota", 1, false)).isTrue();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/state", 1, false)).isTrue();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/event", 1, false)).isTrue();
    }

    @Test
    void deniesWildcardsCrossDeviceWrongDirectionNonCanonicalTopicsNonQosOneAndRetain() {
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/+/command", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/SN-2/command", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-b/product-x/SN-1/state", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/command", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/SN-1/state", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/event", 2, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.SUBSCRIBE,
                "robot/tenant-a/product-x/SN-1/command", 0, null)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/EVENT", 1, false)).isFalse();
        assertThat(service.isAllowed(device, MqttAuthorizationAction.PUBLISH,
                "robot/tenant-a/product-x/SN-1/event", 1, true)).isFalse();
    }
}
