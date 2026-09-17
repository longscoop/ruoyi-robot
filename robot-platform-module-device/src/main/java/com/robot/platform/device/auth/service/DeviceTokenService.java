package com.robot.platform.device.auth.service;

import com.robot.platform.device.auth.DeviceTokenAuthenticationException;
import com.robot.platform.device.identity.service.DeviceHttpAuthenticationIdentity;
import com.robot.platform.device.identity.service.DeviceIdentityService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.crypto.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

/** Issues short-lived DEVICE sessions after exact HMAC verification and atomically consuming a nonce. */
@Service
public class DeviceTokenService {
    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final String NONCE_PREFIX = "robot:device:hmac-nonce:";
    private final DeviceIdentityService identities;
    private final SecretCipher cipher;
    private final StringRedisTemplate redis;
    private final DeviceSessionTokenService sessions;
    private final Clock clock;
    private final Duration allowedSkew;
    private final Duration nonceTtl;

    @Autowired
    public DeviceTokenService(DeviceIdentityService identities, SecretCipher cipher, StringRedisTemplate redis,
                              DeviceSessionTokenService sessions,
                              @Value("${robot.device.hmac-clock-skew:PT2M}") Duration allowedSkew,
                              @Value("${robot.device.nonce-ttl:PT10M}") Duration nonceTtl) {
        this(identities, cipher, redis, sessions, Clock.systemUTC(), allowedSkew, nonceTtl);
    }
    public DeviceTokenService(DeviceIdentityService identities, SecretCipher cipher, StringRedisTemplate redis,
                              DeviceSessionTokenService sessions, Clock clock, Duration allowedSkew, Duration nonceTtl) {
        this.identities = identities;
        this.cipher = cipher;
        this.redis = redis;
        this.sessions = sessions;
        this.clock = clock;
        this.allowedSkew = allowedSkew;
        this.nonceTtl = nonceTtl;
    }

    public DeviceTokenResult issue(DeviceTokenRequest request) {
        if (!validRequest(request) || outsideAllowedWindow(request.timestamp())) throw invalid();
        try {
            DeviceHttpAuthenticationIdentity identity = identities.findForHttpAuthentication(request.deviceSn());
            byte[] expected = hmac(cipher.decrypt(identity.httpSecretCiphertext()), canonical(request));
            byte[] supplied = Base64.getUrlDecoder().decode(request.signature());
            if (!MessageDigest.isEqual(expected, supplied)) throw invalid();
            Boolean consumed = redis.opsForValue().setIfAbsent(nonceKey(request), "1", nonceTtl);
            if (!Boolean.TRUE.equals(consumed)) throw invalid();
            String token = sessions.issue(new DeviceSession(identity.tenantId(), identity.deviceId(), identity.robotId(),
                    identity.deviceSn(), identity.credentialVersion(), ApiAudience.DEVICE));
            return new DeviceTokenResult(token, ApiAudience.DEVICE);
        } catch (DeviceTokenAuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private boolean outsideAllowedWindow(long timestamp) {
        return Math.abs(clock.instant().getEpochSecond() - timestamp) > allowedSkew.toSeconds();
    }
    private static boolean validRequest(DeviceTokenRequest request) {
        return request != null && request.deviceSn() != null && COMPONENT.matcher(request.deviceSn()).matches()
                && request.nonce() != null && COMPONENT.matcher(request.nonce()).matches()
                && request.signature() != null && request.signature().matches("[A-Za-z0-9_-]{43}");
    }
    private static String canonical(DeviceTokenRequest request) {
        return "POST\n/device-api/auth/token\n" + request.deviceSn() + "\n" + request.timestamp() + "\n" + request.nonce();
    }
    private static byte[] hmac(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
    private static String nonceKey(DeviceTokenRequest request) {
        return NONCE_PREFIX + request.deviceSn() + ':' + request.nonce();
    }
    private static DeviceTokenAuthenticationException invalid() { return new DeviceTokenAuthenticationException(); }
}
