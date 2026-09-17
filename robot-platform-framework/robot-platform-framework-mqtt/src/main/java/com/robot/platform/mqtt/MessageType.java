package com.robot.platform.mqtt;

/** Protocol message names. A message is usable only when its version is registered with a data DTO. */
public enum MessageType {
    HEARTBEAT,
    MISSION_START,
    MISSION_CANCEL,
    MISSION_ACK,
    MISSION_EVENT,
    OTA_COMMAND
}
