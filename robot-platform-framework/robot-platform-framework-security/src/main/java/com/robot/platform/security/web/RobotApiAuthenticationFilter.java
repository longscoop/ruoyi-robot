package com.robot.platform.security.web;

import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import com.robot.platform.security.session.RobotSession;
import com.robot.platform.security.session.RobotSessionTokenResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Enforces a single typed opaque-token audience for one API namespace. */
public final class RobotApiAuthenticationFilter extends OncePerRequestFilter {
    private final String apiPrefix;
    private final String anonymousPath;
    private final ApiAudience audience;
    private final SubjectType subjectType;
    private final RobotSessionTokenResolver resolver;

    public RobotApiAuthenticationFilter(String apiPrefix, String anonymousPath, ApiAudience audience,
                                        SubjectType subjectType, RobotSessionTokenResolver resolver) {
        this.apiPrefix = apiPrefix;
        this.anonymousPath = anonymousPath;
        this.audience = audience;
        this.subjectType = subjectType;
        this.resolver = resolver;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(apiPrefix + "/") || anonymousPath.equals(path);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization;
        RobotSession session = token == null || token.isBlank() ? null : resolver.resolveSession(token);
        if (session == null || session.audience() != audience || session.subjectType() != subjectType) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        SecurityFrameworkUtils.setLoginUser(new RobotAuthenticatedPrincipal(session.tenantId(), session.subjectId(),
                session.audience(), session.subjectType()), request);
        chain.doFilter(request, response);
    }
}
