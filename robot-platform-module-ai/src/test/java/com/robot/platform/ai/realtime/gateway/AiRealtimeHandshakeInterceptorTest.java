package com.robot.platform.ai.realtime.gateway;

import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRealtimeHandshakeInterceptorTest {

    @Mock private DeviceSessionTokenService tokenService;
    @Mock private WebSocketHandler webSocketHandler;

    @Test
    void missingDeviceTokenRejectsUpgrade() {
        var interceptor = new AiRealtimeHandshakeInterceptor(tokenService);
        var attributes = new HashMap<String, Object>();

        assertFalse(interceptor.beforeHandshake(request("/device-api/ai/realtime", null),
                response(), webSocketHandler, attributes));

        assertTrue(attributes.isEmpty());
        verifyNoInteractions(tokenService);
    }

    @Test
    void invalidDeviceTokenRejectsUpgrade() {
        when(tokenService.resolve("dev_invalid")).thenReturn(null);
        var interceptor = new AiRealtimeHandshakeInterceptor(tokenService);

        assertFalse(interceptor.beforeHandshake(request("/device-api/ai/realtime", "Bearer dev_invalid"),
                response(), webSocketHandler, new HashMap<>()));

        verify(tokenService).resolve("dev_invalid");
    }

    @Test
    void authenticatedHandshakeStoresExactTrustedDeviceSessionOnly() {
        DeviceSession trusted = new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
        when(tokenService.resolve("dev_good")).thenReturn(trusted);
        var interceptor = new AiRealtimeHandshakeInterceptor(tokenService);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(
                request("/device-api/ai/realtime?tenantId=999&deviceId=888&robotId=777", "Bearer dev_good"),
                response(), webSocketHandler, attributes));

        assertSame(trusted, attributes.get(AiRealtimeHandshakeInterceptor.DEVICE_SESSION_ATTRIBUTE));
        assertEquals(1, attributes.size());
        assertFalse(attributes.containsKey("tenantId"));
        assertFalse(attributes.containsKey("deviceId"));
        assertFalse(attributes.containsKey("robotId"));
        assertFalse(attributes.values().contains("dev_good"));
        verify(tokenService).resolve("dev_good");
    }

    @Test
    void nonDeviceAudienceSessionRejectsUpgradeEvenIfResolverReturnsIt() {
        DeviceSession wrongAudience = new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.APP);
        when(tokenService.resolve("dev_wrong")).thenReturn(wrongAudience);
        var interceptor = new AiRealtimeHandshakeInterceptor(tokenService);

        assertFalse(interceptor.beforeHandshake(request("/device-api/ai/realtime", "dev_wrong"),
                response(), webSocketHandler, new HashMap<>()));
    }

    private static ServletServerHttpRequest request(String uri, String authorization) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            servlet.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return new ServletServerHttpRequest(servlet);
    }

    private static ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }
}
