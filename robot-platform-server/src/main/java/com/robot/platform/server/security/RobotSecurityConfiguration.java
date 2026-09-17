package com.robot.platform.server.security;

import cn.iocoder.yudao.framework.security.config.SecurityFilterChainCustomizer;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import com.robot.platform.security.web.RobotAdminNamespaceFilter;
import com.robot.platform.security.web.RobotApiAuthenticationFilter;
import com.robot.platform.security.web.RobotNamespaceCredentialConflictFilter;
import com.robot.platform.security.web.RobotWebSocketAppAuthenticationFilter;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import com.robot.platform.security.session.RobotSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;

/** Adds DEVICE authentication without changing the authoritative upstream ADMIN OAuth filter. */
@Component
@Order(200)
@RequiredArgsConstructor
public class RobotSecurityConfiguration implements SecurityFilterChainCustomizer {
    private final DeviceSessionTokenService sessions;
    private final MemberSessionTokenService memberSessions;
    private final SecurityProperties securityProperties;
    @Value("${yudao.websocket.path:/ws}")
    private String webSocketPath;

    @Override public void customize(HttpSecurity httpSecurity) {
        httpSecurity.addFilterBefore(new RobotNamespaceCredentialConflictFilter(securityProperties), UsernamePasswordAuthenticationFilter.class);
        httpSecurity.addFilterBefore(new RobotAdminNamespaceFilter(securityProperties), UsernamePasswordAuthenticationFilter.class);
        httpSecurity.addFilterBefore(new RobotApiAuthenticationFilter("/device-api", "/device-api/auth/token",
                ApiAudience.DEVICE, SubjectType.DEVICE, sessions), UsernamePasswordAuthenticationFilter.class);
        httpSecurity.addFilterBefore(new RobotWebSocketAppAuthenticationFilter(webSocketPath, securityProperties, token -> {
                    var session = memberSessions.resolve(token);
                    return session == null ? null : new RobotSession(session.tenantId(), session.memberId(), session.audience(), session.subjectType());
                }),
                UsernamePasswordAuthenticationFilter.class);
    }
}
