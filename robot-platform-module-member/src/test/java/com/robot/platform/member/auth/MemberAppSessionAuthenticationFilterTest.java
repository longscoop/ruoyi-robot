package com.robot.platform.member.auth;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.member.auth.security.MemberAppSessionAuthenticationFilter;
import com.robot.platform.member.auth.service.MemberSession;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemberAppSessionAuthenticationFilterTest {
    private final MemberSessionTokenService sessions = mock(MemberSessionTokenService.class);
    private final MemberAppSessionAuthenticationFilter filter = new MemberAppSessionAuthenticationFilter(sessions);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void rejectsAdminAndDeviceLikeTokensAtAppBoundary() throws Exception {
        for (String token : new String[]{"admin_token", "device_token"}) {
            HttpServletRequest request = request(token); HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilter(request, response, mock(FilterChain.class));
            verify(response).sendError(401);
        }
    }
    @Test void acceptsResolvedTypedAppMemberSession() throws Exception {
        when(sessions.resolve("app_valid")).thenReturn(new MemberSession(10L, 7L, ApiAudience.APP, SubjectType.MEMBER));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("app_valid"), mock(HttpServletResponse.class), chain);
        assertThat(SecurityFrameworkUtils.getLoginUser()).isInstanceOf(RobotAuthenticatedPrincipal.class);
        verify(chain).doFilter(any(), any());
    }
    private static HttpServletRequest request(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/app-api/member/profile"); when(request.getContextPath()).thenReturn("");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token); return request;
    }
}
