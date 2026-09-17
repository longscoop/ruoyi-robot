package com.robot.platform.mqtt;

import java.util.concurrent.CompletionStage;

/** A completed stage means the broker accepted a QoS 1, non-retained message, not that a robot acted on it. */
public interface RobotMqttPublisher {
    CompletionStage<Void> publish(RobotTopic topic, RobotMessageEnvelope<?> envelope);
}
