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

/** Prevents platform APP and DEVICE opaque tokens from ever entering the upstream ADMIN token flow. */
public final class RobotAdminNamespaceFilter extends OncePerRequestFilter {
    private final SecurityProperties securityProperties;

    public RobotAdminNamespaceFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/admin-api/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configuredToken = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(),
                securityProperties.getTokenParameter());
        // Device and APP clients historically use the standard Authorization carrier even when Admin is configured otherwise.
        // Reuse the legacy parser so Bearer stripping remains identical to the upstream token filter.
        String standardAuthorizationToken = SecurityFrameworkUtils.obtainAuthorization(request, HttpHeaders.AUTHORIZATION,
                securityProperties.getTokenParameter());
        if (isRobotOpaqueToken(configuredToken) || isRobotOpaqueToken(standardAuthorizationToken)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        chain.doFilter(request, response);
    }
    private static boolean isRobotOpaqueToken(String token) {
        return token != null && (token.startsWith("app_") || token.startsWith("dev_"));
    }
}
