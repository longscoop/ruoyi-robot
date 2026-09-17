package com.robot.platform.robot.mission.service.dto;

import com.robot.platform.robot.mission.enums.MissionStatus;

import java.time.LocalDateTime;

public record MissionRespDTO(Long id, String missionNo, Long robotId, String missionType, String source,
                             MissionStatus status, Integer priority, String requestId, LocalDateTime scheduledTime,
                             LocalDateTime startedTime, LocalDateTime finishedTime, String errorCode,
                             String errorMessage) { }
