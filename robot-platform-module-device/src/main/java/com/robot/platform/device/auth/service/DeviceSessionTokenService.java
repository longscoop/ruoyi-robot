package com.robot.platform.device.auth.service;

import com.robot.platform.device.device.service.DeviceCredentialsRevokedEvent;
import com.robot.platform.device.identity.service.DeviceHttpAuthenticationIdentity;
import com.robot.platform.device.identity.service.DeviceIdentityService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.session.RobotSession;
import com.robot.platform.security.session.RobotSessionTokenResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/** Redis-backed DEVICE sessions, revalidated against the current device lifecycle and credential version. */
@Service
public class DeviceSessionTokenService implements RobotSessionTokenResolver {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SESSION_PREFIX = "robot:device:session:";
    private static final String INDEX_PREFIX = "robot:device:session-index:";
    private final StringRedisTemplate redis;
    private final DeviceIdentityService identities;
    private final Duration ttl;

    public DeviceSessionTokenService(StringRedisTemplate redis, DeviceIdentityService identities,
                                     @Value("${robot.device.session-ttl:PT15M}") Duration ttl) {
        this.redis = redis;
        this.identities = identities;
        this.ttl = ttl;
    }

    public String issue(DeviceSession session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = "dev_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(SESSION_PREFIX + token, encode(session), ttl);
        String index = index(session.deviceId());
        redis.opsForSet().add(index, token);
        redis.expire(index, ttl);
        return token;
    }

    public DeviceSession resolve(String token) {
        if (token == null || !token.startsWith("dev_")) return null;
        String encoded = redis.opsForValue().get(SESSION_PREFIX + token);
        DeviceSession session = decode(encoded);
        if (session == null || session.audience() != ApiAudience.DEVICE || !current(session)) {
            revoke(token, session == null ? null : session.deviceId());
            return null;
        }
        return session;
    }

    @Override public RobotSession resolveSession(String token) {
        DeviceSession session = resolve(token);
        return session == null ? null : session.asRobotSession();
    }

    @EventListener
    public void revoke(DeviceCredentialsRevokedEvent event) {
        Set<String> tokens = redis.opsForSet().members(index(event.deviceId()));
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(token -> SESSION_PREFIX + token).toList());
        }
        redis.delete(index(event.deviceId()));
    }

    private boolean current(DeviceSession session) {
        try {
            DeviceHttpAuthenticationIdentity identity = identities.findForHttpAuthentication(session.deviceSn());
            return identity.deviceId() == session.deviceId() && identity.tenantId() == session.tenantId()
                    && identity.robotId() == session.robotId() && identity.credentialVersion() == session.credentialVersion();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
    private void revoke(String token, Long deviceId) {
        redis.delete(SESSION_PREFIX + token);
        if (deviceId != null) redis.opsForSet().remove(index(deviceId), token);
    }
    private static String index(long deviceId) { return INDEX_PREFIX + deviceId; }
    private static String encode(DeviceSession session) {
        return "tenantId=" + session.tenantId() + ";deviceId=" + session.deviceId() + ";robotId=" + session.robotId()
                + ";deviceSn=" + session.deviceSn() + ";credentialVersion=" + session.credentialVersion() + ";audience=DEVICE";
    }
    private static DeviceSession decode(String value) {
        if (value == null) return null;
        String[] fields = value.split(";", -1);
        if (fields.length != 6 || !fields[0].startsWith("tenantId=") || !fields[1].startsWith("deviceId=")
                || !fields[2].startsWith("robotId=") || !fields[3].startsWith("deviceSn=")
                || !fields[4].startsWith("credentialVersion=") || !"audience=DEVICE".equals(fields[5])) return null;
        try {
            return new DeviceSession(Long.parseLong(fields[0].substring(9)), Long.parseLong(fields[1].substring(9)),
                    Long.parseLong(fields[2].substring(8)), fields[3].substring(9),
                    Integer.parseInt(fields[4].substring(18)), ApiAudience.DEVICE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
