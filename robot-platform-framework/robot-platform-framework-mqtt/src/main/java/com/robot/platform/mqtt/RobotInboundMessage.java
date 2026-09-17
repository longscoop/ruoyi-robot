package com.robot.platform.mqtt;

/** Decoded MQTT input paired with its canonical topic for future server-resolved context adapters. */
public record RobotInboundMessage<T>(RobotTopic topic, RobotMessageEnvelope<T> envelope) { }
