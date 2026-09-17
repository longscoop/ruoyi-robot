package com.robot.platform.member.auth.service;

import com.robot.platform.member.member.dal.dataobject.MemberDO;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import com.robot.platform.member.member.enums.MemberStatus;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.member.member.enums.MemberErrorCodeConstants.*;

/** Password provider compares only BCrypt-compatible stored hashes and never logs credentials. */
@Service
public class PasswordMemberAuthenticationProvider implements MemberAuthenticationProvider {
    private static final String DUMMY_PASSWORD = "member-login-dummy-password";
    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public PasswordMemberAuthenticationProvider(MemberMapper memberMapper, PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Override public MemberSession authenticate(MemberLoginCommand command) {
        MemberDO member = memberMapper.selectByTenantAndMobile(command.tenantId(), command.mobile());
        String passwordHash = member == null ? dummyPasswordHash : member.getPassword();
        boolean passwordMatches = passwordEncoder.matches(command.password(), passwordHash);
        if (member == null || MemberStatus.DISABLED.name().equals(member.getStatus()) || !passwordMatches) {
            throw exception(MEMBER_LOGIN_INVALID);
        }
        return new MemberSession(member.getTenantId(), member.getId(), ApiAudience.APP, SubjectType.MEMBER);
    }
}
