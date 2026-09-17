package com.robot.platform.device.mqtt.controller;

import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceTopicAuthorizationService;
import com.robot.platform.mqtt.MqttAuthorizationAction;
import com.robot.platform.mqtt.RobotMqttProperties;
import com.robot.platform.mqtt.RobotTopic;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** EMQX AuthZ callback: every operation is evaluated using fresh server-side identity data. */
@RestController
@TenantIgnore
@PermitAll
public class EmqxAuthorizationController {
    private static final Map<String, Object> DENY = Map.of("result", "deny");
    private final DeviceMqttAuthenticationService identities;
    private final DeviceTopicAuthorizationService authorization;
    private final MqttCallbackRateLimiter limiter;
    private final RobotMqttProperties properties;

    public EmqxAuthorizationController(DeviceMqttAuthenticationService identities, DeviceTopicAuthorizationService authorization,
                                       MqttCallbackRateLimiter limiter, RobotMqttProperties properties) {
        this.identities = identities;
        this.authorization = authorization;
        this.limiter = limiter;
        this.properties = properties;
    }

    @PostMapping("/mqtt-api/authorize")
    public Map<String, Object> authorize(@RequestHeader(value = "x-mqtt-callback-token", required = false) String token,
                                         @RequestBody Map<String, Object> body) {
        if (!new EmqxCallbackTokenVerifier(properties.getCallbackToken()).matches(token)) return DENY;
        try {
            String username = text(body, "username");
            if (username == null) return DENY;
            RobotMqttProperties.Cloud cloud = properties.getCloud();
            boolean cloudPrincipal = constantEquals(cloud.getUsername(), username);
            // Named requests consume principal capacity even when later authorization fields are malformed.
            if (!limiter.tryAcquire("authorize", username,
                    cloudPrincipal ? MqttCallbackRateLimiter.PrincipalType.CLOUD
                            : MqttCallbackRateLimiter.PrincipalType.DEVICE)) return DENY;

            String clientId = text(body, "clientid");
            String topic = text(body, "topic");
            if (clientId == null || topic == null) return DENY;
            MqttAuthorizationAction action = MqttAuthorizationAction.parse(text(body, "action"));
            Integer qos = positiveQos(text(body, "qos"));
            Boolean retain = retain(text(body, "retain"));
            String principalType = clientAttribute(body, "principal_type");
            Integer credentialVersion = integer(clientAttribute(body, "credential_version"));
            RobotTopic parsedTopic = parseCanonicalTopic(topic);
            boolean stateWildcard = "robot/+/+/+/state".equals(topic);
            if (qos == null || credentialVersion == null || (!stateWildcard && parsedTopic == null)) return DENY;

            if (cloudPrincipal) {
                return "CLOUD".equals(principalType) && clientId.equals(cloud.getClientId())
                        && credentialVersion == cloud.getCredentialVersion()
                        && cloudAllowed(action, parsedTopic, stateWildcard, retain) ? allow() : DENY;
            }
            boolean allowed = "DEVICE".equals(principalType) && clientId.equals(username)
                    && identities.findActiveByUsername(username)
                    .map(identity -> identity.credentialVersion() == credentialVersion
                            && authorization.isAllowed(identity, action, topic, qos, retain)).orElse(false);
            return allowed ? allow() : DENY;
        } catch (RuntimeException ignored) {
            return DENY;
        }
    }

    private static Map<String, Object> allow() { return Map.of("result", "allow"); }

    private static boolean cloudAllowed(MqttAuthorizationAction action, RobotTopic topic, boolean stateWildcard, Boolean retain) {
        if (action == MqttAuthorizationAction.PUBLISH && !Boolean.FALSE.equals(retain)) return false;
        if (stateWildcard) return action == MqttAuthorizationAction.SUBSCRIBE && retain == null;
        if (topic == null) return false;
        return switch (action) {
            case PUBLISH -> topic.channel() == RobotTopic.Channel.COMMAND || topic.channel() == RobotTopic.Channel.OTA;
            case SUBSCRIBE -> topic.channel() == RobotTopic.Channel.STATE || topic.channel() == RobotTopic.Channel.EVENT;
        };
    }

    private static RobotTopic parseCanonicalTopic(String rawTopic) {
        try {
            RobotTopic topic = RobotTopic.parse(rawTopic);
            return rawTopic.equals(topic.value()) ? topic : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clientAttribute(Map<String, Object> body, String name) {
        Object nested = body == null ? null : body.get("client_attrs");
        if (nested instanceof Map<?, ?> attributes && attributes.get(name) instanceof String value) return value;
        return text(body, "client_attrs." + name);
    }

    private static Integer positiveQos(String value) { return "1".equals(value) ? 1 : null; }
    private static Boolean retain(String value) { return value == null ? null : "false".equals(value) ? false : "true".equals(value) ? true : null; }
    private static Integer integer(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }
    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
    private static boolean constantEquals(String expected, String actual) {
        return new EmqxCallbackTokenVerifier(expected).matches(actual);
    }
}
