package com.robot.platform.member.auth;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.member.auth.service.MemberLoginCommand;
import com.robot.platform.member.auth.service.MemberSession;
import com.robot.platform.member.auth.service.PasswordMemberAuthenticationProvider;
import com.robot.platform.member.member.dal.dataobject.MemberDO;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import com.robot.platform.member.member.enums.MemberStatus;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Password authentication must reject every non-enabled account before issuing an APP session. */
class MemberPasswordAuthenticationProviderTest {
    private static final String DUMMY_HASH = "$2a$04$configuredCostDummyHash................................";
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    {
        when(passwordEncoder.encode("member-login-dummy-password")).thenReturn(DUMMY_HASH);
    }
    private final PasswordMemberAuthenticationProvider provider = new PasswordMemberAuthenticationProvider(memberMapper, passwordEncoder);

    @Test
    void issuesAppAudienceSessionForValidPassword() {
        MemberDO enabledMember = member(7L, MemberStatus.ENABLED);
        when(memberMapper.selectByTenantAndMobile(10L, "13800138000")).thenReturn(enabledMember);
        when(passwordEncoder.matches("correct", enabledMember.getPassword())).thenReturn(true);

        MemberSession session = provider.authenticate(new MemberLoginCommand(10L, "13800138000", "correct"));

        assertThat(session.tenantId()).isEqualTo(10L);
        assertThat(session.memberId()).isEqualTo(7L);
        assertThat(session.audience()).isEqualTo(ApiAudience.APP);
        assertThat(session.subjectType()).isEqualTo(SubjectType.MEMBER);
    }

    @Test
    void rejectsWrongPasswordWithoutReturningCredentialDetails() {
        MemberDO enabledMember = member(7L, MemberStatus.ENABLED);
        when(memberMapper.selectByTenantAndMobile(10L, "13800138000")).thenReturn(enabledMember);
        when(passwordEncoder.matches("wrong", enabledMember.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(new MemberLoginCommand(10L, "13800138000", "wrong")))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsDisabledMemberEvenWhenPasswordMatches() {
        MemberDO disabledMember = member(7L, MemberStatus.DISABLED);
        when(memberMapper.selectByTenantAndMobile(10L, "13800138000")).thenReturn(disabledMember);

        assertThatThrownBy(() -> provider.authenticate(new MemberLoginCommand(10L, "13800138000", "correct")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");
        verify(passwordEncoder).matches("correct", disabledMember.getPassword());
    }

    @Test
    void unknownDisabledAndWrongPasswordShareOneInvalidCredentialsContract() {
        MemberDO enabledMember = member(7L, MemberStatus.ENABLED);
        when(memberMapper.selectByTenantAndMobile(10L, "wrong")).thenReturn(enabledMember);
        when(passwordEncoder.matches("wrong", enabledMember.getPassword())).thenReturn(false);
        when(memberMapper.selectByTenantAndMobile(10L, "disabled")).thenReturn(member(8L, MemberStatus.DISABLED));

        assertThatThrownBy(() -> provider.authenticate(new MemberLoginCommand(10L, "unknown", "p")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");
        assertThatThrownBy(() -> provider.authenticate(new MemberLoginCommand(10L, "wrong", "wrong")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");
        assertThatThrownBy(() -> provider.authenticate(new MemberLoginCommand(10L, "disabled", "p")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");
        verify(passwordEncoder, times(3)).matches(anyString(), anyString());
    }

    @Test
    void usesConfiguredEncoderForDummyAndStoredWrongPasswordComparisons() {
        MemberMapper mapper = mock(MemberMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("member-login-dummy-password")).thenReturn(DUMMY_HASH);
        MemberDO enabledMember = member(7L, MemberStatus.ENABLED);
        when(mapper.selectByTenantAndMobile(10L, "known")).thenReturn(enabledMember);
        when(encoder.matches("wrong", enabledMember.getPassword())).thenReturn(false);
        PasswordMemberAuthenticationProvider localProvider = new PasswordMemberAuthenticationProvider(mapper, encoder);

        assertThatThrownBy(() -> localProvider.authenticate(new MemberLoginCommand(10L, "unknown", "wrong")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");
        assertThatThrownBy(() -> localProvider.authenticate(new MemberLoginCommand(10L, "known", "wrong")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号或密码错误");

        verify(encoder).encode("member-login-dummy-password");
        verify(encoder).matches("wrong", DUMMY_HASH);
        verify(encoder).matches("wrong", enabledMember.getPassword());
        verifyNoMoreInteractions(encoder);
    }

    private static MemberDO member(long id, MemberStatus status) {
        return MemberDO.builder().id(id).tenantId(10L).mobile("13800138000")
                .password("$2a$10$storedStrongHashOnly").status(status.name()).build();
    }
}
