package com.robot.platform.security.web;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects mixed Admin and APP/DEVICE carriers so legacy Admin authentication cannot replace a typed principal. */
public final class RobotNamespaceCredentialConflictFilter extends OncePerRequestFilter {
    private final SecurityProperties securityProperties;

    public RobotNamespaceCredentialConflictFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/app-api/") && !path.startsWith("/device-api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String standardToken = SecurityFrameworkUtils.obtainAuthorization(request, HttpHeaders.AUTHORIZATION,
                securityProperties.getTokenParameter());
        if (isRobotOpaqueToken(standardToken) && !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(securityProperties.getTokenHeader())) {
            String configuredToken = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(),
                    securityProperties.getTokenParameter());
            if (configuredToken != null) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isRobotOpaqueToken(String token) {
        return token != null && (token.startsWith("app_") || token.startsWith("dev_"));
    }
}
