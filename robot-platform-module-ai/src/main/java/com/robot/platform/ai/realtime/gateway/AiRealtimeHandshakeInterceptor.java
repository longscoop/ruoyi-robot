package com.robot.platform.ai.realtime.gateway;

import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.security.ApiAudience;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class AiRealtimeHandshakeInterceptor implements HandshakeInterceptor {

    public static final String DEVICE_SESSION_ATTRIBUTE =
            AiRealtimeHandshakeInterceptor.class.getName() + ".deviceSession";

    private final DeviceSessionTokenService tokenService;

    public AiRealtimeHandshakeInterceptor(DeviceSessionTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractAuthorizationToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        DeviceSession session = tokenService.resolve(token);
        if (!validDeviceSession(session)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Persist only the trusted identity. Never retain the opaque credential in WebSocket attributes.
        attributes.put(DEVICE_SESSION_ATTRIBUTE, session);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String extractAuthorizationToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (value.startsWith("Bearer ")) {
            value = value.substring(7).trim();
        }
        return value.isBlank() ? null : value;
    }

    private static boolean validDeviceSession(DeviceSession session) {
        return session != null
                && session.audience() == ApiAudience.DEVICE
                && session.tenantId() > 0
                && session.deviceId() > 0
                && session.robotId() > 0;
    }
}
