package com.robot.platform.device.auth;

import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.device.auth.service.DeviceTokenRequest;
import com.robot.platform.device.auth.service.DeviceTokenResult;
import com.robot.platform.device.auth.service.DeviceTokenService;
import com.robot.platform.device.identity.service.DeviceHttpAuthenticationIdentity;
import com.robot.platform.device.identity.service.DeviceIdentityService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.crypto.AesGcmSecretCipher;
import com.robot.platform.security.crypto.SecretCipher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private static final String HTTP_SECRET = "device-http-secret";
    private final DeviceIdentityService identities = mock(DeviceIdentityService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final DeviceSessionTokenService sessions = mock(DeviceSessionTokenService.class);
    private final SecretCipher cipher = new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final DeviceTokenService service = new DeviceTokenService(identities, cipher, redis, sessions,
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2), Duration.ofMinutes(10));

    DeviceTokenServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(identities.findForHttpAuthentication("SN-001")).thenReturn(identity());
        when(sessions.issue(any(DeviceSession.class))).thenReturn("dev_opaque");
    }

    @Test
    void issuesTypedDeviceSessionForExactCanonicalHmac() {
        DeviceTokenResult result = service.issue(signed("SN-001", NOW.getEpochSecond(), "nonce-1", HTTP_SECRET));

        assertThat(result.accessToken()).isEqualTo("dev_opaque");
        assertThat(result.audience()).isEqualTo(ApiAudience.DEVICE);
        verify(sessions).issue(new DeviceSession(10L, 7L, 9L, "SN-001", 4, ApiAudience.DEVICE));
        verify(values).setIfAbsent(contains("SN-001:nonce-1"), eq("1"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void acceptsIndependentRfcStyleNewlineHmacVector() {
        // Generated independently with OpenSSL over these exact UTF-8 bytes (0A is a newline):
        // 504f53540a2f6465766963652d6170692f617574682f746f6b656e0a534e2d5246432d3030310a313730303030303030300a6e6f6e63652d7266632d303031
        // SHA-256 HMAC hex: 6991b95d49dd4b634f0ca3267c69831e1148eb47097c8bbbefd55a3c15144892
        // Standard Base64: aZG5XUndS2NPDKMmfGmDHhFI60cJfIu779VaPBUUSJI=
        when(identities.findForHttpAuthentication("SN-RFC-001")).thenReturn(new DeviceHttpAuthenticationIdentity(
                7L, 10L, 9L, "SN-RFC-001", 4, cipher.encrypt("rfc-device-secret")));

        DeviceTokenService rfcService = new DeviceTokenService(identities, cipher, redis, sessions,
                Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC), Duration.ofMinutes(2), Duration.ofMinutes(10));
        DeviceTokenResult result = rfcService.issue(new DeviceTokenRequest("SN-RFC-001", 1_700_000_000L,
                "nonce-rfc-001", "aZG5XUndS2NPDKMmfGmDHhFI60cJfIu779VaPBUUSJI"));

        assertThat(result.accessToken()).isEqualTo("dev_opaque");
    }

    @Test
    void rejectsDisabledAuthoritativeIdentity() {
        when(identities.findForHttpAuthentication("SN-disabled")).thenThrow(new IllegalStateException("inactive"));

        assertThatThrownBy(() -> service.issue(signed("SN-disabled", NOW.getEpochSecond(), "nonce-disabled", HTTP_SECRET)))
                .isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(sessions, never()).issue(any());
    }

    @Test
    void rejectsUnboundAuthoritativeIdentity() {
        when(identities.findForHttpAuthentication("SN-unbound")).thenThrow(new IllegalStateException("unbound"));

        assertThatThrownBy(() -> service.issue(signed("SN-unbound", NOW.getEpochSecond(), "nonce-unbound", HTTP_SECRET)))
                .isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(sessions, never()).issue(any());
    }

    @Test
    void rejectsTamperedSignatureBeforeIssuingSession() {
        DeviceTokenRequest signed = signed("SN-001", NOW.getEpochSecond(), "nonce-1", HTTP_SECRET);
        DeviceTokenRequest request = new DeviceTokenRequest(signed.deviceSn(), signed.timestamp(), signed.nonce(), signed.signature() + "x");

        assertThatThrownBy(() -> service.issue(request)).isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(sessions, never()).issue(any());
    }

    @Test
    void rejectsExpiredTimestampBeforeNonceConsumption() {
        assertThatThrownBy(() -> service.issue(signed("SN-001", NOW.minusSeconds(121).getEpochSecond(), "nonce-1", HTTP_SECRET)))
                .isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    void rejectsReplayedNonceAtomically() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true, false);
        DeviceTokenRequest request = signed("SN-001", NOW.getEpochSecond(), "nonce-1", HTTP_SECRET);
        service.issue(request);

        assertThatThrownBy(() -> service.issue(request)).isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(sessions, times(1)).issue(any());
    }

    @Test
    void invalidSignatureDoesNotConsumeNonceButAReplayOfSuccessfulRequestDoes() {
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true, false);
        DeviceTokenRequest correct = signed("SN-001", NOW.getEpochSecond(), "nonce-shared", HTTP_SECRET);
        String changedFirstCharacter = correct.signature().startsWith("A") ? "B" : "A";
        DeviceTokenRequest invalid = new DeviceTokenRequest(correct.deviceSn(), correct.timestamp(), correct.nonce(),
                changedFirstCharacter + correct.signature().substring(1));

        assertThatThrownBy(() -> service.issue(invalid)).isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any());

        assertThat(service.issue(correct).accessToken()).isEqualTo("dev_opaque");
        assertThatThrownBy(() -> service.issue(correct)).isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(values, times(2)).setIfAbsent(contains("SN-001:nonce-shared"), eq("1"), eq(Duration.ofMinutes(10)));
        verify(sessions, times(1)).issue(any());
    }

    @Test
    void rejectsCiphertextThatFailsAesGcmAuthentication() {
        when(identities.findForHttpAuthentication("SN-001")).thenReturn(new DeviceHttpAuthenticationIdentity(
                7L, 10L, 9L, "SN-001", 4, "v1:AAAAAAAAAAAAAAAA:AAAA"));

        assertThatThrownBy(() -> service.issue(signed("SN-001", NOW.getEpochSecond(), "nonce-1", HTTP_SECRET)))
                .isInstanceOf(DeviceTokenAuthenticationException.class);
        verify(sessions, never()).issue(any());
    }

    private DeviceHttpAuthenticationIdentity identity() {
        return new DeviceHttpAuthenticationIdentity(7L, 10L, 9L, "SN-001", 4, cipher.encrypt(HTTP_SECRET));
    }

    private static DeviceTokenRequest signed(String deviceSn, long timestamp, String nonce, String secret) {
        String canonical = "POST\n/device-api/auth/token\n" + deviceSn + "\n" + timestamp + "\n" + nonce;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new DeviceTokenRequest(deviceSn, timestamp, nonce,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
