package com.robot.platform.device.mqtt.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs before MVC and request-body parsing, so malformed or unauthenticated callbacks are bounded too. */
@Component
@Order(Integer.MIN_VALUE + 100)
public class MqttCallbackPreAuthRateLimitFilter extends OncePerRequestFilter {
    private static final int TOO_MANY_REQUESTS = 429;
    private final MqttCallbackPreAuthRateLimiter limiter;

    public MqttCallbackPreAuthRateLimitFilter(MqttCallbackPreAuthRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/mqtt-api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Callback peers are brokers: untrusted forwarded headers must not create fresh rate buckets.
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write("{\"result\":\"deny\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
