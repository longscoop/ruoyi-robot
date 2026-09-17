package com.robot.platform.robot.status;

import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.robot.status.controller.device.DeviceHeartbeatController;
import com.robot.platform.robot.status.controller.device.vo.DeviceRobotConfigRespVO;
import com.robot.platform.robot.status.service.DeviceHeartbeatApplicationService;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeviceHeartbeatControllerTest {
    private final DeviceHeartbeatApplicationService application = mock(DeviceHeartbeatApplicationService.class);
    private final DeviceHeartbeatController controller = new DeviceHeartbeatController(application);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void heartbeatDelegatesOnlyAuthenticatedTenantAndDeviceIdentity() {
        authenticate(new RobotAuthenticatedPrincipal(10, 4, ApiAudience.DEVICE, SubjectType.DEVICE));
        byte[] body = "strict-envelope".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(application.accept(10, 4, body)).thenReturn(RobotHeartbeatService.AcceptResult.ACCEPTED);

        assertThat(controller.heartbeat(body).getData()).isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);

        verify(application).accept(10, 4, body);
    }

    @Test
    void configUsesIndependentDeviceConfigurationContractAndRejectsAdminPrincipal() {
        authenticate(new RobotAuthenticatedPrincipal(10, 4, ApiAudience.DEVICE, SubjectType.DEVICE));
        when(application.config(10, 4)).thenReturn(new DeviceRobotConfigRespVO(7, 30));

        assertThat(controller.config().getData()).isEqualTo(new DeviceRobotConfigRespVO(7, 30));

        authenticate(new RobotAuthenticatedPrincipal(10, 1, ApiAudience.ADMIN, SubjectType.ADMIN));
        assertThatThrownBy(controller::config).isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(RobotAuthenticatedPrincipal principal) {
        SecurityFrameworkUtils.setLoginUser(principal, new MockHttpServletRequest());
    }
}
