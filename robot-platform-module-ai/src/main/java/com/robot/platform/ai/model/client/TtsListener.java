package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;

@FunctionalInterface
public interface TtsListener {

    void onEvent(ProviderEvent event);
}
