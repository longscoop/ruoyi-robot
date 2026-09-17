package com.robot.platform.security.web;

import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import com.robot.platform.security.session.RobotSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/** Authenticates APP opaque tokens for the WebSocket handshake without changing legacy Admin tokens. */
public final class RobotWebSocketAppAuthenticationFilter extends OncePerRequestFilter {
    private final String path;
    private final SecurityProperties securityProperties;
    private final Function<String, RobotSession> resolver;
    public RobotWebSocketAppAuthenticationFilter(String path, SecurityProperties securityProperties,
                                                  Function<String, RobotSession> resolver) {
        this.path = path;
        this.securityProperties = securityProperties;
        this.resolver = resolver;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).equals(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Set<String> tokens = tokens(request);
        if (tokens.size() > 1) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        String token = tokens.stream().findFirst().orElse(null);
        // The upstream security filter remains authoritative for legacy ADMIN tokens.
        if (token != null && token.startsWith("app_")) {
            RobotSession session = resolver.apply(token);
            if (session == null || session.audience() != ApiAudience.APP || session.subjectType() != SubjectType.MEMBER || session.tenantId() <= 0) {
                response.sendError(HttpStatus.UNAUTHORIZED.value()); return;
            }
            SecurityFrameworkUtils.setLoginUser(new RobotAuthenticatedPrincipal(session.tenantId(), session.subjectId(),
                    session.audience(), session.subjectType()), request);
        }
        chain.doFilter(request, response);
    }

    /** All configured and legacy carriers participate so no lower-priority carrier can override a principal. */
    private Set<String> tokens(HttpServletRequest request) {
        Set<String> tokens = new LinkedHashSet<>();
        addHeaders(tokens, request, "Authorization");
        addHeaders(tokens, request, securityProperties.getTokenHeader());
        addParameters(tokens, request, "token");
        addParameters(tokens, request, securityProperties.getTokenParameter());
        return tokens;
    }

    private static void addHeaders(Set<String> tokens, HttpServletRequest request, String name) {
        java.util.Enumeration<String> values = request.getHeaders(name);
        while (values.hasMoreElements()) add(tokens, values.nextElement());
    }

    private static void addParameters(Set<String> tokens, HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values != null) for (String value : values) add(tokens, value);
    }

    private static void add(Set<String> tokens, String raw) {
        if (raw == null || raw.isBlank()) return;
        int bearer = raw.indexOf("Bearer ");
        String token = bearer >= 0 ? raw.substring(bearer + 7).trim() : raw.trim();
        if (!token.isBlank()) tokens.add(token);
    }
}
