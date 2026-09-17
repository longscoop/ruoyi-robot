package com.robot.platform.device.mqtt.controller;

import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.RobotMqttProperties;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** EMQX AuthN callback. Only the documented EMQX allow/deny response is exposed. */
@RestController
@TenantIgnore
@PermitAll
public class EmqxAuthenticationController {
    private static final Map<String, Object> DENY = Map.of("result", "deny");
    private final DeviceMqttAuthenticationService identities;
    private final PasswordEncoder passwordEncoder;
    private final MqttCallbackRateLimiter limiter;
    private final RobotMqttProperties properties;

    public EmqxAuthenticationController(DeviceMqttAuthenticationService identities, PasswordEncoder passwordEncoder,
                                        MqttCallbackRateLimiter limiter, RobotMqttProperties properties) {
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.properties = properties;
    }

    @PostMapping("/mqtt-api/authenticate")
    public Map<String, Object> authenticate(@RequestHeader(value = "x-mqtt-callback-token", required = false) String token,
                                            @RequestBody Map<String, Object> body) {
        if (!new EmqxCallbackTokenVerifier(properties.getCallbackToken()).matches(token)) return DENY;
        String username = text(body, "username");
        String password = text(body, "password");
        String clientId = text(body, "clientid");
        if (username == null || password == null || clientId == null) return DENY;

        RobotMqttProperties.Cloud cloud = properties.getCloud();
        boolean cloudPrincipal = constantEquals(cloud.getUsername(), username);
        if (!limiter.tryAcquire("authenticate", username,
                cloudPrincipal ? MqttCallbackRateLimiter.PrincipalType.CLOUD : MqttCallbackRateLimiter.PrincipalType.DEVICE)) return DENY;
        if (cloudPrincipal) {
            return constantEquals(cloud.getPassword(), password) && clientId.equals(cloud.getClientId())
                    ? allow("CLOUD", cloud.getCredentialVersion()) : DENY;
        }
        try {
            Optional<DeviceMqttIdentity> identity = identities.findActiveByUsername(username);
            if (identity.isEmpty() || !clientId.equals(username)
                    || !passwordEncoder.matches(password, identity.get().mqttSecretHash())) return DENY;
            return allow("DEVICE", identity.get().credentialVersion());
        } catch (RuntimeException ignored) {
            return DENY;
        }
    }

    private static Map<String, Object> allow(String principalType, int credentialVersion) {
        // EMQX client attributes are wire strings, even when they represent an integer version.
        return Map.of("result", "allow", "is_superuser", false,
                "client_attrs", Map.of("principal_type", principalType,
                        "credential_version", Integer.toString(credentialVersion)));
    }

    private static String text(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static boolean constantEquals(String expected, String actual) {
        return new EmqxCallbackTokenVerifier(expected).matches(actual);
    }
}
