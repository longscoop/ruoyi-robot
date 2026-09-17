package com.robot.platform.member.auth.security;

import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.member.auth.service.MemberSession;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Resolves only opaque member APP tokens; admin and device credentials are rejected at the namespace boundary. */
@RequiredArgsConstructor
public class MemberAppSessionAuthenticationFilter extends OncePerRequestFilter {
    private final MemberSessionTokenService sessions;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/app-api/") || "/app-api/auth/login".equals(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization;
        if (token == null || !token.startsWith("app_")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        MemberSession session = sessions.resolve(token);
        if (session == null || session.audience() != ApiAudience.APP || session.subjectType() != SubjectType.MEMBER) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        SecurityFrameworkUtils.setLoginUser(new RobotAuthenticatedPrincipal(session.tenantId(), session.memberId(),
                session.audience(), session.subjectType()), request);
        chain.doFilter(request, response);
    }
}
