package com.robot.platform.ai.model.client;

public interface ChatModelClient {

    String providerType();

    default String modelType() {
        return "CHAT";
    }

    ChatStream stream(ChatRequest request, ChatListener listener);
}
