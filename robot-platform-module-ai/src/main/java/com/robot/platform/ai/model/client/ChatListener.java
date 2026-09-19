package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;

@FunctionalInterface
public interface ChatListener {

    void onEvent(ProviderEvent event);
}
