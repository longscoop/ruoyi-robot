package com.robot.platform.robot.mission.message;

import com.fasterxml.jackson.databind.JsonNode;

/** Stable command body. Action contents remain server-validated mission data, not device-supplied input. */
public record MissionStartPayload(long missionId, JsonNode payload) { }
