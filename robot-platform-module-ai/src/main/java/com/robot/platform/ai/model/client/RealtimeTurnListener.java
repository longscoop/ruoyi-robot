package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;

@FunctionalInterface
public interface RealtimeTurnListener {

    void onEvent(String turnId, long generation, ProviderEvent event);
}
