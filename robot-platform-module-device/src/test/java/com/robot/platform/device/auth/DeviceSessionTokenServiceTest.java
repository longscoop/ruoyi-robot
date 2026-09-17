package com.robot.platform.device.auth;

import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.device.device.service.DeviceCredentialsRevokedEvent;
import com.robot.platform.device.identity.service.DeviceHttpAuthenticationIdentity;
import com.robot.platform.device.identity.service.DeviceIdentityService;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceSessionTokenServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked") private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final DeviceIdentityService identities = mock(DeviceIdentityService.class);
    private final DeviceSessionTokenService sessions = new DeviceSessionTokenService(redis, identities, Duration.ofMinutes(15));

    DeviceSessionTokenServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
    }

    @Test
    void rejectsSessionImmediatelyWhenCurrentCredentialVersionChanges() {
        DeviceSession session = new DeviceSession(10L, 7L, 9L, "SN-001", 4, ApiAudience.DEVICE);
        String token = sessions.issue(session);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(values).set(contains(token), payload.capture(), eq(Duration.ofMinutes(15)));
        when(values.get(contains(token))).thenReturn(payload.getValue());
        when(identities.findForHttpAuthentication("SN-001")).thenReturn(identity(4), identity(5));

        assertThat(sessions.resolve(token)).isEqualTo(session);
        assertThat(sessions.resolve(token)).isNull();
        verify(redis).delete(contains(token));
    }

    @Test
    void revocationEventDeletesIndexedSessions() {
        when(sets.members("robot:device:session-index:7")).thenReturn(Set.of("dev_one", "dev_two"));

        sessions.revoke(new DeviceCredentialsRevokedEvent(7L, 10L, 5, "ROTATED"));

        verify(redis).delete(argThat((java.util.Collection<String> keys) -> keys.size() == 2 && keys.containsAll(
                Set.of("robot:device:session:dev_one", "robot:device:session:dev_two"))));
        verify(redis).delete("robot:device:session-index:7");
    }

    private static DeviceHttpAuthenticationIdentity identity(int version) {
        return new DeviceHttpAuthenticationIdentity(7L, 10L, 9L, "SN-001", version, "ciphertext");
    }
}
