package com.robot.platform.robot.mission.message;

/**
 * A cancellation is a request to the robot; the terminal state still comes from an authenticated result.
 * When {@code targetStartMessageId} is present, the robot MUST retain it as a tombstone and reject
 * any later MISSION_START with that envelope message id. This makes a cancel-first MQTT delivery safe.
 */
public record MissionCancelPayload(long missionId, String reason, String targetStartMessageId) { }
