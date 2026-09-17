package com.robot.platform.integration;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.security.config.YudaoSecurityAutoConfiguration;
import cn.iocoder.yudao.framework.security.config.YudaoWebSecurityConfigurerAdapter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.device.mqtt.controller.EmqxAuthenticationController;
import com.robot.platform.device.mqtt.controller.EmqxAuthorizationController;
import com.robot.platform.device.mqtt.controller.MqttCallbackPreAuthRateLimitFilter;
import com.robot.platform.device.mqtt.controller.MqttCallbackPreAuthRateLimiter;
import com.robot.platform.device.mqtt.controller.MqttCallbackRateLimiter;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceTopicAuthorizationService;
import com.robot.platform.member.auth.security.MemberAppSecurityFilterCustomizer;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import com.robot.platform.mqtt.RobotMqttProperties;
import com.robot.platform.server.security.RobotSecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the production annotation scanner and APP/DEVICE/ADMIN filters, with external stores mocked. */
@WebMvcTest(controllers = {EmqxAuthenticationController.class, EmqxAuthorizationController.class})
@ActiveProfiles("unit-test")
@ContextConfiguration(classes = {MqttCallbackSecurityChainTest.Ports.class,
        EmqxAuthenticationController.class, EmqxAuthorizationController.class})
@ImportAutoConfiguration({YudaoSecurityAutoConfiguration.class, YudaoWebSecurityConfigurerAdapter.class})
@Import({EmqxAuthenticationController.class, EmqxAuthorizationController.class,
        MqttCallbackPreAuthRateLimitFilter.class, RobotSecurityConfiguration.class,
        MemberAppSecurityFilterCustomizer.class})
class MqttCallbackSecurityChainTest {
    private static final String AUTHENTICATE = """
            {"username":"cloud","clientid":"cloud-client","password":"cloud-password"}
            """;
    private static final String AUTHORIZE = """
            {"username":"cloud","clientid":"cloud-client","topic":"robot/t/p/d/command",
             "action":"publish","qos":"1","retain":"false",
             "client_attrs":{"principal_type":"CLOUD","credential_version":"1"}}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private DeviceMqttAuthenticationService identities;
    @MockitoBean private DeviceSessionTokenService deviceSessions;
    @MockitoBean private MemberSessionTokenService memberSessions;
    @MockitoBean private MqttCallbackRateLimiter principalLimiter;
    @MockitoBean private MqttCallbackPreAuthRateLimiter preAuthLimiter;
    @MockitoBean private OAuth2TokenCommonApi oauth2Tokens;
    @MockitoBean private PermissionCommonApi permissions;
    @MockitoBean private GlobalExceptionHandler exceptionHandler;
    @MockitoBean private AuthorizeRequestsCustomizer unrelatedAuthorizationRules;

    @BeforeEach
    void allowRateBudgets() {
        when(preAuthLimiter.tryAcquire(any())).thenReturn(true);
        when(principalLimiter.tryAcquire(any(), any(), any())).thenReturn(true);
    }

    @Test
    void permitAllAnnotationsLetAnonymousCallbacksReachTheirOwnTokenProtection() throws Exception {
        for (String path : new String[]{"/mqtt-api/authenticate", "/mqtt-api/authorize"}) {
            String body = path.endsWith("authenticate") ? AUTHENTICATE : AUTHORIZE;
            mvc.perform(post(path).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("deny"));
            mvc.perform(post(path).header("x-mqtt-callback-token", "wrong-token")
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("deny"));
        }
        verifyNoInteractions(principalLimiter, identities, deviceSessions, memberSessions, oauth2Tokens);

        mvc.perform(post("/mqtt-api/authenticate").header("x-mqtt-callback-token", "callback-secret")
                        .contentType("application/json").content(AUTHENTICATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("allow"));
        mvc.perform(post("/mqtt-api/authorize").header("x-mqtt-callback-token", "callback-secret")
                        .contentType("application/json").content(AUTHORIZE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("allow"));
    }

    @Test
    void productionSecurityChainDoesNotBlanketPermitTheMqttNamespace() throws Exception {
        mvc.perform(post("/mqtt-api/not-a-callback"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(principalLimiter, identities);
    }

    @Test
    void preAuthLimitRejectsMalformedCallbacksBeforeMvcAndPrincipalLookup() throws Exception {
        when(preAuthLimiter.tryAcquire(any())).thenReturn(false);

        mvc.perform(post("/mqtt-api/authenticate").header("x-mqtt-callback-token", "wrong-token")
                        .contentType("application/json").content("not json"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.result").value("deny"));
        verifyNoInteractions(principalLimiter, identities);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Ports {
        @Bean
        WebProperties webProperties() { return new WebProperties(); }

        @Bean
        DeviceTopicAuthorizationService topicAuthorization() { return new DeviceTopicAuthorizationService(); }

        @Bean
        RobotMqttProperties robotMqttProperties() {
            RobotMqttProperties properties = new RobotMqttProperties();
            properties.setCallbackToken("callback-secret");
            properties.getCloud().setClientId("cloud-client");
            properties.getCloud().setUsername("cloud");
            properties.getCloud().setPassword("cloud-password");
            return properties;
        }
    }
}
