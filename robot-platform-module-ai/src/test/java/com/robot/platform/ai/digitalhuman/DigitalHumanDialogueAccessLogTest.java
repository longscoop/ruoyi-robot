package com.robot.platform.ai.digitalhuman;

import com.robot.platform.ai.digitalhuman.controller.admin.DigitalHumanDialogueController;
import com.robot.platform.ai.digitalhuman.service.DigitalHumanDialogueSessions;
import com.robot.platform.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import com.robot.platform.framework.common.util.spring.SpringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalHumanDialogueAccessLogTest {
    @Test void developmentLoggingDoesNotReadRawMicrophonePayload() throws Exception {
        var controller = new DigitalHumanDialogueController(mock(DigitalHumanDialogueSessions.class));
        var handler = new HandlerMethod(controller, DigitalHumanDialogueController.class.getMethod("turn",
                long.class, String.class, DigitalHumanDialogueController.Audio.class));
        var request = spy(new MockHttpServletRequest("POST", "/admin-api/ai/digital-humans/42/dialogue-sessions/test/turns"));
        var response = new MockHttpServletResponse();
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(SpringUtils::isProd).thenReturn(false);
            var interceptor = new ApiAccessLogInterceptor();
            assertTrue(interceptor.preHandle(request, response, handler));
            interceptor.afterCompletion(request, response, handler, null);
            verify(request, never()).getInputStream();
            verify(request, never()).getParameterMap();
        }
    }
}
