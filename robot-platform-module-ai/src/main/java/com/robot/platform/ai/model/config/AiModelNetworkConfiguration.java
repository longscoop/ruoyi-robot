package com.robot.platform.ai.model.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.*;
import java.net.http.HttpClient;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Controls AI model traffic without changing the JVM or operating-system proxy settings. */
@Configuration(proxyBeanMethods = false)
public class AiModelNetworkConfiguration {
    @Bean(name = {"aiModelHttpClient", "platformChatHttpClient"})
    public HttpClient aiModelHttpClient(@Value("${robot.ai.model-network.proxy-mode:SYSTEM}") String mode) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "SYSTEM" -> { /* Preserve the JDK's default proxy selector. */ }
            case "DIRECT" -> builder.proxy(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
            });
            default -> throw new IllegalArgumentException("robot.ai.model-network.proxy-mode must be SYSTEM or DIRECT");
        }
        org.slf4j.LoggerFactory.getLogger(AiModelNetworkConfiguration.class)
                .info("AI model network proxy mode: {}", mode.trim().toUpperCase(Locale.ROOT));
        return builder.build();
    }
}
