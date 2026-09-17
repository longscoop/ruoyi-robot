package com.robot.platform.member.auth.security;

import cn.iocoder.yudao.framework.security.config.SecurityFilterChainCustomizer;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/** Inserts APP token handling into the real Spring Security chain before legacy OAuth token handling. */
@Component
@Order(100)
@RequiredArgsConstructor
public class MemberAppSecurityFilterCustomizer implements SecurityFilterChainCustomizer {
    private final MemberSessionTokenService sessions;
    @Override public void customize(HttpSecurity httpSecurity) {
        // The shared configuration invokes customizers before it adds its legacy OAuth filter.
        // Using the standard anchor gives both filters a registered order; insertion order keeps this first.
        httpSecurity.addFilterBefore(new MemberAppSessionAuthenticationFilter(sessions), UsernamePasswordAuthenticationFilter.class);
    }
}
