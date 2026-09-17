package com.robot.platform.robot.command.gateway;

/** Transport-neutral command. Only a gateway implementation may turn it into MQTT/Outbox records. */
public record RobotCommand(long tenantId, long robotId, long missionId, String requestId, String type, String payload) { }
