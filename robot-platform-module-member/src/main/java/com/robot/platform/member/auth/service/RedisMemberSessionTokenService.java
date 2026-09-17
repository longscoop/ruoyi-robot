package com.robot.platform.member.auth.service;

import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** Redis-backed, revocable opaque member sessions. Redis values contain no password or token. */
@Service
public class RedisMemberSessionTokenService implements MemberSessionTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "robot:member:session:";
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    public RedisMemberSessionTokenService(StringRedisTemplate redisTemplate,
            @Value("${robot.member.session-ttl:PT720H}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override public String issue(MemberSession session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = "app_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(PREFIX + token, encode(session), ttl);
        return token;
    }
    @Override public MemberSession resolve(String token) {
        if (token == null || !token.startsWith("app_")) return null;
        String value = redisTemplate.opsForValue().get(PREFIX + token);
        if (value == null) return null;
        return decode(value);
    }
    @Override public void revoke(String token) {
        if (token != null && token.startsWith("app_")) redisTemplate.delete(PREFIX + token);
    }
    private static String encode(MemberSession session) {
        return "tenantId=" + session.tenantId() + ";memberId=" + session.memberId()
                + ";audience=" + session.audience() + ";subjectType=" + session.subjectType();
    }
    private static MemberSession decode(String value) {
        String[] fields = value.split(";", -1);
        if (fields.length != 4 || !fields[0].startsWith("tenantId=") || !fields[1].startsWith("memberId=")
                || !"audience=APP".equals(fields[2]) || !"subjectType=MEMBER".equals(fields[3])) return null;
        try { return new MemberSession(Long.parseLong(fields[0].substring(9)), Long.parseLong(fields[1].substring(9)),
                ApiAudience.APP, SubjectType.MEMBER); }
        catch (NumberFormatException ignored) { return null; }
    }
}
