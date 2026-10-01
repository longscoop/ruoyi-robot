package com.robot.platform.ai.model.chat.platform;

import com.robot.platform.ai.model.client.ChatModelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class PlatformChatClients {
    @Bean
    ChatModelClient qwenChatModelClient(HttpClient platformChatHttpClient) {
        return new PlatformChatModelClient("QWEN", platformChatHttpClient);
    }

    @Bean
    ChatModelClient fastGptChatModelClient(HttpClient platformChatHttpClient) {
        return new PlatformChatModelClient("FASTGPT", platformChatHttpClient);
    }

    @Bean
    ChatModelClient difyChatModelClient(HttpClient platformChatHttpClient) {
        return new PlatformChatModelClient("DIFY", platformChatHttpClient);
    }

    @Bean
    ChatModelClient cozeChatModelClient(HttpClient platformChatHttpClient) {
        return new PlatformChatModelClient("COZE", platformChatHttpClient);
    }
}
