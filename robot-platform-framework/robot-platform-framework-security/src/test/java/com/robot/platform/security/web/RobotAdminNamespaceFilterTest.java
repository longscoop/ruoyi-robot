package com.robot.platform.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.robot.platform.framework.security.config.SecurityProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RobotAdminNamespaceFilterTest {
    @Test
    void blocksStandardAuthorizationBearerEvenWhenUpstreamUsesDifferentHeader() throws Exception {
        RobotAdminNamespaceFilter filter = new RobotAdminNamespaceFilter(properties());
        for (String token : new String[]{"app_member", "dev_robot"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/system/users");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            assertBlocked(filter, request);
        }
    }

    @Test
    void blocksConfiguredNonstandardHeaderTokenCarrier() throws Exception {
        RobotAdminNamespaceFilter filter = new RobotAdminNamespaceFilter(properties());
        for (String token : new String[]{"app_member", "dev_robot"}) {
            assertBlocked(filter, requestWithHeader(token));
        }
    }

    @Test
    void blocksConfiguredQueryTokenCarrier() throws Exception {
        RobotAdminNamespaceFilter filter = new RobotAdminNamespaceFilter(properties());
        for (String token : new String[]{"app_member", "dev_robot"}) {
            assertBlocked(filter, requestWithQuery(token));
        }
    }

    private static void assertBlocked(RobotAdminNamespaceFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("cross-audience request reached the admin chain");
        });
        assertThat(response.getStatus()).isEqualTo(401);
    }
    private static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenHeader("X-Access-Token");
        properties.setTokenParameter("access_token");
        return properties;
    }
    private static MockHttpServletRequest requestWithQuery(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/system/users");
        request.setParameter("access_token", token);
        return request;
    }
    private static MockHttpServletRequest requestWithHeader(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/system/users");
        request.addHeader("X-Access-Token", token);
        return request;
    }
}
