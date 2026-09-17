package com.robot.platform.device.mqtt;

import com.robot.platform.device.mqtt.controller.EmqxAuthenticationController;
import com.robot.platform.device.mqtt.controller.EmqxAuthorizationController;
import com.robot.platform.device.mqtt.controller.MqttCallbackRateLimiter;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.device.mqtt.service.DeviceTopicAuthorizationService;
import com.robot.platform.mqtt.RobotMqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmqxCallbackControllerTest {
    private final DeviceMqttIdentity identity = new DeviceMqttIdentity(7L, 10L, 9L, "tenant-a", "product-x", "SN-1",
            "tenant-a/product-x/SN-1", "password-hash", 4);
    private final DeviceMqttAuthenticationService identities = mock(DeviceMqttAuthenticationService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final MqttCallbackRateLimiter limiter = (endpoint, ip, username) -> true;
    private final RobotMqttProperties properties = properties();

    @Test
    void authenticationFailsClosedForMissingWrongAndInvalidCredentials() {
        when(identities.findActiveByUsername(identity.mqttUsername())).thenReturn(Optional.of(identity));
        when(passwords.matches("wrong", "password-hash")).thenReturn(false);
        EmqxAuthenticationController controller = authn();

        assertThat(controller.authenticate(null, deviceAuth(identity.mqttUsername(), "wrong"))).containsEntry("result", "deny");
        assertThat(controller.authenticate("not-the-token", deviceAuth(identity.mqttUsername(), "wrong"))).containsEntry("result", "deny");
        assertThat(controller.authenticate("callback-secret", deviceAuth(identity.mqttUsername(), "wrong"))).containsEntry("result", "deny");
        verify(passwords).matches("wrong", "password-hash");
    }

    @Test
    void invalidCallbackTokenNeverConsumesPostTokenPrincipalCapacity() {
        MqttCallbackRateLimiter principalLimiter = mock(MqttCallbackRateLimiter.class);
        EmqxAuthenticationController controller = new EmqxAuthenticationController(identities, passwords, principalLimiter, properties);

        assertThat(controller.authenticate("wrong-token", deviceAuth(identity.mqttUsername(), "password"))).containsEntry("result", "deny");

        verifyNoInteractions(principalLimiter);
    }

    @Test
    void authenticationReturnsStringClientAttributesAfterPasswordAndClientIdMatch() {
        when(identities.findActiveByUsername(identity.mqttUsername())).thenReturn(Optional.of(identity));
        when(passwords.matches("correct", "password-hash")).thenReturn(true);

        assertThat(authn().authenticate("callback-secret", deviceAuth(identity.mqttUsername(), "correct")))
                .containsEntry("result", "allow")
                .containsEntry("client_attrs", Map.of("principal_type", "DEVICE", "credential_version", "4"));
        assertThat(authn().authenticate("callback-secret", Map.of("username", identity.mqttUsername(), "clientid", "other", "password", "correct")))
                .containsEntry("result", "deny");
    }

    @Test
    void authenticationDeniesUnknownUsersAndPasswordEncoderFailuresWithoutDetails() {
        when(identities.findActiveByUsername("unknown/product/SN")).thenReturn(Optional.empty());
        when(identities.findActiveByUsername(identity.mqttUsername())).thenReturn(Optional.of(identity));
        when(passwords.matches("bad-hash", "password-hash")).thenThrow(new IllegalArgumentException("bad encoded password"));

        assertThat(authn().authenticate("callback-secret", deviceAuth("unknown/product/SN", "p"))).containsExactlyEntriesOf(Map.of("result", "deny"));
        assertThat(authn().authenticate("callback-secret", deviceAuth(identity.mqttUsername(), "bad-hash"))).containsExactlyEntriesOf(Map.of("result", "deny"));
    }

    @Test
    void cloudAuthenticationRequiresConfiguredClientIdAndReturnsStringAttributes() {
        assertThat(authn().authenticate("callback-secret", Map.of("username", "cloud", "clientid", "cloud-client", "password", "cloud-password")))
                .containsEntry("client_attrs", Map.of("principal_type", "CLOUD", "credential_version", "1"));
        assertThat(authn().authenticate("callback-secret", Map.of("username", "cloud", "clientid", "other", "password", "cloud-password")))
                .containsEntry("result", "deny");
    }

    @Test
    void authorizationUsesCurrentIdentityStringAttributesAndDoesNotTrustTenantInput() {
        when(identities.findActiveByUsername(identity.mqttUsername())).thenReturn(Optional.of(identity));
        Map<String, Object> request = deviceAuthorize("publish", "robot/tenant-a/product-x/SN-1/event", "1", "false");

        assertThat(authz().authorize("callback-secret", request)).containsEntry("result", "allow");
        assertThat(authz().authorize("callback-secret", replace(request, "client_attrs", Map.of("principal_type", "DEVICE", "credential_version", "3"))))
                .containsEntry("result", "deny");
    }

    @Test
    void authorizationRequiresMatchingPrincipalClientIdCanonicalTopicAndExactlyQosOne() {
        Map<String, Object> validCloud = cloudAuthorize("publish", "robot/t/p/d/command", "1", "false");
        assertThat(authz().authorize("callback-secret", validCloud)).containsEntry("result", "allow");
        assertThat(authz().authorize("callback-secret", replace(validCloud, "clientid", "other"))).containsEntry("result", "deny");
        assertThat(authz().authorize("callback-secret", replace(validCloud, "topic", "robot/t/p/d/COMMAND"))).containsEntry("result", "deny");
        assertThat(authz().authorize("callback-secret", replace(validCloud, "qos", "0"))).containsEntry("result", "deny");
        assertThat(authz().authorize("callback-secret", replace(validCloud, "client_attrs", Map.of("principal_type", "DEVICE", "credential_version", "1"))))
                .containsEntry("result", "deny");
    }

    @Test
    void subscribeMayOmitRetainButCloudDirectionAndCallbackTokenRemainRestricted() {
        assertThat(authz().authorize("callback-secret", cloudAuthorize("subscribe", "robot/t/p/d/state", "1", null))).containsEntry("result", "allow");
        assertThat(authz().authorize("callback-secret", cloudAuthorize("subscribe", "robot/+/+/+/state", "1", null))).containsEntry("result", "allow");
        assertThat(authz().authorize("callback-secret", cloudAuthorize("subscribe", "robot/t/p/d/command", "1", null))).containsEntry("result", "deny");
        assertThat(authz().authorize("callback-secret", cloudAuthorize("publish", "robot/+/+/+/state", "1", "false"))).containsEntry("result", "deny");
        assertThat(authz().authorize("callback-secret", cloudAuthorize("subscribe", "robot/+/+/+/event", "1", null))).containsEntry("result", "deny");
        assertThat(authz().authorize(null, cloudAuthorize("subscribe", "robot/t/p/d/state", "1", null))).containsEntry("result", "deny");
    }

    @Test
    void namedMalformedAuthorizationConsumesItsTypedPrincipalBudgetBeforeFieldParsing() {
        MqttCallbackRateLimiter principalLimiter = mock(MqttCallbackRateLimiter.class);
        when(principalLimiter.tryAcquire(anyString(), anyString(), any())).thenReturn(false);
        EmqxAuthorizationController controller = new EmqxAuthorizationController(
                identities, new DeviceTopicAuthorizationService(), principalLimiter, properties);
        Map<String, Object> device = deviceAuthorize("publish", "robot/tenant-a/product-x/SN-1/event", "1", "false");
        Map<String, Object> cloud = cloudAuthorize("publish", "robot/t/p/d/command", "1", "false");

        for (Map<String, Object> request : malformedVariants(device)) {
            assertThat(controller.authorize("callback-secret", request)).containsExactlyEntriesOf(Map.of("result", "deny"));
        }
        for (Map<String, Object> request : malformedVariants(cloud)) {
            assertThat(controller.authorize("callback-secret", request)).containsExactlyEntriesOf(Map.of("result", "deny"));
        }

        verify(principalLimiter, times(5)).tryAcquire(
                "authorize", identity.mqttUsername(), MqttCallbackRateLimiter.PrincipalType.DEVICE);
        verify(principalLimiter, times(5)).tryAcquire(
                "authorize", "cloud", MqttCallbackRateLimiter.PrincipalType.CLOUD);
        verifyNoMoreInteractions(principalLimiter);
    }

    @Test
    void invalidTokenOrMissingUsernameNeverConsumesAuthorizationPrincipalCapacity() {
        MqttCallbackRateLimiter principalLimiter = mock(MqttCallbackRateLimiter.class);
        EmqxAuthorizationController controller = new EmqxAuthorizationController(
                identities, new DeviceTopicAuthorizationService(), principalLimiter, properties);
        Map<String, Object> valid = cloudAuthorize("subscribe", "robot/t/p/d/state", "1", null);

        assertThat(controller.authorize("wrong-token", valid)).containsExactlyEntriesOf(Map.of("result", "deny"));
        assertThat(controller.authorize("callback-secret", without(valid, "username")))
                .containsExactlyEntriesOf(Map.of("result", "deny"));

        verifyNoInteractions(principalLimiter);
    }

    private EmqxAuthenticationController authn() { return new EmqxAuthenticationController(identities, passwords, limiter, properties); }
    private EmqxAuthorizationController authz() { return new EmqxAuthorizationController(identities, new DeviceTopicAuthorizationService(), limiter, properties); }
    private static Map<String, Object> deviceAuth(String username, String password) { return Map.of("username", username, "clientid", username, "password", password); }
    private Map<String, Object> deviceAuthorize(String action, String topic, String qos, String retain) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>(Map.of("username", identity.mqttUsername(), "clientid", identity.mqttUsername(), "action", action,
                "topic", topic, "qos", qos, "client_attrs", Map.of("principal_type", "DEVICE", "credential_version", "4"), "tenantId", "999"));
        if (retain != null) body.put("retain", retain);
        return body;
    }
    private static Map<String, Object> cloudAuthorize(String action, String topic, String qos, String retain) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>(Map.of("username", "cloud", "clientid", "cloud-client", "action", action,
                "topic", topic, "qos", qos, "client_attrs", Map.of("principal_type", "CLOUD", "credential_version", "1")));
        if (retain != null) body.put("retain", retain);
        return body;
    }
    private static Map<String, Object> replace(Map<String, Object> source, String key, Object value) {
        java.util.HashMap<String, Object> copy = new java.util.HashMap<>(source); copy.put(key, value); return copy;
    }
    private static java.util.List<Map<String, Object>> malformedVariants(Map<String, Object> valid) {
        return java.util.List.of(
                replace(valid, "action", "invalid"),
                replace(valid, "qos", "not-a-qos"),
                replace(valid, "retain", "not-a-boolean"),
                replace(valid, "client_attrs", Map.of("principal_type", "INVALID")),
                replace(valid, "topic", "not/a/robot/topic"));
    }
    private static Map<String, Object> without(Map<String, Object> source, String key) {
        java.util.HashMap<String, Object> copy = new java.util.HashMap<>(source); copy.remove(key); return copy;
    }
    private static RobotMqttProperties properties() {
        RobotMqttProperties properties = new RobotMqttProperties();
        properties.setCallbackToken("callback-secret");
        properties.getCloud().setClientId("cloud-client");
        properties.getCloud().setUsername("cloud");
        properties.getCloud().setPassword("cloud-password");
        properties.getCloud().setCredentialVersion(1);
        return properties;
    }
}
