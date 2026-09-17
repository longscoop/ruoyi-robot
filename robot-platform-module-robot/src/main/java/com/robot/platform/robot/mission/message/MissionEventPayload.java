package com.robot.platform.robot.mission.message;

/** Typed Mission progress/result event. kind is ACTION or RESULT; actionId is mandatory only for ACTION. */
public record MissionEventPayload(String kind, long missionId, Long actionId, String status,
                                  String errorCode, String errorMessage) { }
