package com.robot.platform.device.mqtt;

import com.robot.platform.device.mqtt.controller.MqttCallbackPreAuthRateLimitFilter;
import com.robot.platform.device.mqtt.controller.MqttCallbackPreAuthRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MqttCallbackPreAuthRateLimitFilterTest {
    @Test
    void rejectsOverLimitCallbackBeforeMvcRegardlessOfTokenOrMalformedBody() throws Exception {
        MqttCallbackPreAuthRateLimiter limiter = mock(MqttCallbackPreAuthRateLimiter.class);
        when(limiter.tryAcquire("192.0.2.10")).thenReturn(false);
        MqttCallbackPreAuthRateLimitFilter filter = new MqttCallbackPreAuthRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/platform/mqtt-api/authenticate");
        request.setContextPath("/platform");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.42");
        request.addHeader("x-mqtt-callback-token", "wrong-token");
        request.setContent("not json".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).isEqualTo("{\"result\":\"deny\"}");
        assertThat(chain.getRequest()).isNull();
        verify(limiter).tryAcquire("192.0.2.10");
    }

    @Test
    void allowsPermittedPreAuthCallbackToContinue() throws Exception {
        MqttCallbackPreAuthRateLimiter limiter = mock(MqttCallbackPreAuthRateLimiter.class);
        when(limiter.tryAcquire("192.0.2.10")).thenReturn(true);
        MqttCallbackPreAuthRateLimitFilter filter = new MqttCallbackPreAuthRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mqtt-api/authorize");
        request.setRemoteAddr("192.0.2.10");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(limiter).tryAcquire("192.0.2.10");
    }

    @Test
    void unrelatedRequestsDoNotConsumeCallbackCapacity() throws Exception {
        MqttCallbackPreAuthRateLimiter limiter = mock(MqttCallbackPreAuthRateLimiter.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/device-api/auth/token");
        MockFilterChain chain = new MockFilterChain();

        new MqttCallbackPreAuthRateLimitFilter(limiter).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(limiter);
    }
}
