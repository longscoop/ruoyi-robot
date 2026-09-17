package cn.iocoder.yudao.framework.security.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Extension point for domain token filters that must run inside Spring Security's chain. */
public interface SecurityFilterChainCustomizer {
    void customize(HttpSecurity httpSecurity) throws Exception;
}
