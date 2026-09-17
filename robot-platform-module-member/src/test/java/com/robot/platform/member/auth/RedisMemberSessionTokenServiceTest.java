package com.robot.platform.member.auth;

import com.robot.platform.member.auth.service.MemberSession;
import com.robot.platform.member.auth.service.RedisMemberSessionTokenService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisMemberSessionTokenServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisMemberSessionTokenService sessions = new RedisMemberSessionTokenService(redis, Duration.ofSeconds(2));
    RedisMemberSessionTokenServiceTest() { when(redis.opsForValue()).thenReturn(values); }

    @Test void issuesAndResolvesTypedAppMemberSession() {
        String token = sessions.issue(new MemberSession(10L, 7L, ApiAudience.APP, SubjectType.MEMBER));
        verify(values).set(contains(token), contains("audience=APP"), eq(Duration.ofSeconds(2)));
        when(values.get(contains(token))).thenReturn("tenantId=10;memberId=7;audience=APP;subjectType=MEMBER");
        assertThat(sessions.resolve(token)).isEqualTo(new MemberSession(10L, 7L, ApiAudience.APP, SubjectType.MEMBER));
    }

    @Test void rejectsExpiredMalformedAndWrongAudienceOrSubjectSessions() {
        when(values.get(anyString())).thenReturn(null, "broken", "tenantId=10;memberId=7;audience=ADMIN;subjectType=MEMBER",
                "tenantId=10;memberId=7;audience=APP;subjectType=DEVICE");
        assertThat(sessions.resolve("app_expired")).isNull();
        assertThat(sessions.resolve("app_bad")).isNull();
        assertThat(sessions.resolve("app_admin")).isNull();
        assertThat(sessions.resolve("app_device")).isNull();
    }

    @Test void revocationDeletesOpaqueSession() {
        sessions.revoke("app_value");
        verify(redis).delete(contains("app_value"));
    }
}
