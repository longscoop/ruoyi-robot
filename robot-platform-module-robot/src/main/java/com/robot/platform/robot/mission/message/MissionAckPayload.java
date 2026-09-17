package com.robot.platform.robot.mission.message;

/** Robot acknowledgement for one durable command. commandMessageId binds it to the dispatched command. */
public record MissionAckPayload(long missionId, String commandMessageId, boolean accepted, String reason) { }
