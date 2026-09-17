package com.robot.platform.security.web;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RobotNamespaceCredentialConflictFilterTest {
    @Test
    void rejectsConfiguredAdminCredentialAlongsideStandardAppOrDeviceCredential() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenHeader("X-Access-Token");
        properties.setTokenParameter("access_token");
        RobotNamespaceCredentialConflictFilter filter = new RobotNamespaceCredentialConflictFilter(properties);

        for (String[] requestData : new String[][]{{"/app-api/member/profile", "app_member"},
                {"/device-api/robot/status", "dev_robot"}}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", requestData[0]);
            request.addHeader("Authorization", "Bearer " + requestData[1]);
            request.addHeader("X-Access-Token", "admin-control-1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new AssertionError("conflicting credential request reached upstream authentication");
            });
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
