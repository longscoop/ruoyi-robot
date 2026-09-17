package com.robot.platform.security.web;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import com.robot.platform.security.session.RobotSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RobotWebSocketAppAuthenticationFilterTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void authenticatesOnlyAnAppTokenFromTheConfiguredCarrier() throws Exception {
        SecurityProperties properties = properties();
        RobotWebSocketAppAuthenticationFilter filter = filter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/infra/ws");
        request.addHeader("X-Access-Token", "Bearer app_opaque");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
            assertThat(SecurityFrameworkUtils.getLoginUser().getId()).isEqualTo(7L);
        });
    }

    @Test
    void leavesAdminTokenForTheUpstreamSecurityFilter() throws Exception {
        RobotWebSocketAppAuthenticationFilter filter = filter(properties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/infra/ws");
        request.addHeader("Authorization", "Bearer legacy-admin-token");
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
            reached.set(true);
            assertThat(SecurityFrameworkUtils.getLoginUser()).isNull();
        });

        assertThat(reached).isTrue();
    }

    @Test
    void rejectsEveryConflictingNonBlankCarrierIncludingConfiguredQueryParameter() throws Exception {
        SecurityProperties properties = properties();
        RobotWebSocketAppAuthenticationFilter filter = filter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/infra/ws");
        request.addHeader("Authorization", "Bearer app_one");
        request.setParameter("access_token", "app_two");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("conflicting websocket carriers reached the security chain");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void customCarrierStillRejectsConflictWithLegacyDefaultTokenQueryParameter() throws Exception {
        SecurityProperties properties = properties();
        RobotWebSocketAppAuthenticationFilter filter = filter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/infra/ws");
        request.addHeader("X-Access-Token", "app_opaque");
        request.setParameter("token", "legacy-admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("conflicting websocket carriers reached the security chain");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsTwoDifferentValuesInTheSameHeaderCarrier() throws Exception {
        RobotWebSocketAppAuthenticationFilter filter = filter(properties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/infra/ws");
        request.addHeader("X-Access-Token", "app_opaque");
        request.addHeader("X-Access-Token", "legacy-admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("conflicting websocket header values reached the security chain");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static RobotWebSocketAppAuthenticationFilter filter(SecurityProperties properties) {
        return new RobotWebSocketAppAuthenticationFilter("/infra/ws", properties,
                token -> "app_opaque".equals(token)
                        ? new RobotSession(1L, 7L, ApiAudience.APP, SubjectType.MEMBER) : null);
    }

    private static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenHeader("X-Access-Token");
        properties.setTokenParameter("access_token");
        return properties;
    }
}
