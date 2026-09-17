package com.robot.platform.device.mqtt;

import com.robot.platform.device.mqtt.controller.EmqxAuthenticationController;
import com.robot.platform.device.mqtt.controller.EmqxAuthorizationController;
import com.robot.platform.device.mqtt.controller.MqttCallbackRateLimiter;
import com.robot.platform.device.mqtt.controller.MqttCallbackPreAuthRateLimiter;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceTopicAuthorizationService;
import com.robot.platform.mqtt.RobotMqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC assembly smoke test; the server module separately verifies the production annotation-driven security chain. */
@WebMvcTest(controllers = {EmqxAuthenticationController.class, EmqxAuthorizationController.class})
@AutoConfigureMockMvc
@Import({EmqxCallbackWebMvcTest.PropertiesConfiguration.class, EmqxCallbackWebMvcTest.SecurityConfiguration.class})
@ContextConfiguration(classes = {EmqxCallbackWebMvcTest.WebConfiguration.class,
        EmqxAuthenticationController.class, EmqxAuthorizationController.class})
class EmqxCallbackWebMvcTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private DeviceMqttAuthenticationService identities;
    @MockitoBean private DeviceTopicAuthorizationService authorization;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private MqttCallbackRateLimiter limiter;
    @MockitoBean private MqttCallbackPreAuthRateLimiter preAuthLimiter;

    @Test
    void permitAllCallbackEndpointCreatesControllersAndStillRequiresItsOwnToken() throws Exception {
        when(limiter.tryAcquire(any(), any(), any())).thenReturn(true);
        when(preAuthLimiter.tryAcquire(any())).thenReturn(true);
        mvc.perform(post("/mqtt-api/authenticate").contentType("application/json")
                        .header("x-mqtt-callback-token", "callback-secret")
                        .content("{\"username\":\"cloud\",\"clientid\":\"cloud-client\",\"password\":\"cloud-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("allow"));
        mvc.perform(post("/mqtt-api/authenticate").contentType("application/json")
                        .content("{\"username\":\"cloud\",\"clientid\":\"cloud-client\",\"password\":\"cloud-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("deny"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfiguration {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityConfiguration {
        @Bean
        SecurityFilterChain callbackSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.requestMatchers("/mqtt-api/**").permitAll()
                            .anyRequest().denyAll())
                    .build();
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class WebConfiguration { }
}
