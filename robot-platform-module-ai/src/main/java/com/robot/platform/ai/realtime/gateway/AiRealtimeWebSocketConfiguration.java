package com.robot.platform.ai.realtime.gateway;

import com.robot.platform.ai.realtime.protocol.RealtimeProtocolCodec;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class AiRealtimeWebSocketConfiguration {

    public static final String ENDPOINT = "/device-api/ai/realtime";

    @Bean
    @ConditionalOnMissingBean
    public RealtimeProtocolCodec realtimeProtocolCodec() {
        return new RealtimeProtocolCodec();
    }

    @Bean
    public AiRealtimeHandshakeInterceptor aiRealtimeHandshakeInterceptor(DeviceSessionTokenService tokenService) {
        return new AiRealtimeHandshakeInterceptor(tokenService);
    }

    @Bean
    public AiRealtimeWebSocketHandler aiRealtimeWebSocketHandler(
            RealtimeProtocolCodec codec,
            ObjectProvider<AiRealtimeWebSocketHandler.RuntimeManager> runtimeManagerProvider) {
        return new AiRealtimeWebSocketHandler(codec, runtimeManagerProvider::getIfAvailable);
    }

    @Bean
    public WebSocketConfigurer aiRealtimeWebSocketConfigurer(
            AiRealtimeWebSocketHandler handler,
            AiRealtimeHandshakeInterceptor handshakeInterceptor) {
        return registry -> registry
                .addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
