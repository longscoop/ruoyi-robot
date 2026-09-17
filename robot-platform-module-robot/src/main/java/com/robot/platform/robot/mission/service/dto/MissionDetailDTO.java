package com.robot.platform.robot.mission.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for the admin detail screen. Keeping timelines in the service contract
 * prevents the web controller from bypassing tenant-scoped mission ownership checks.
 */
public record MissionDetailDTO(MissionRespDTO mission, List<Action> actions, List<Event> events) {
    public record Action(Long id, Integer sequenceNo, String actionType, String parameters, String status,
                         LocalDateTime startedTime, LocalDateTime finishedTime, String errorCode, String errorMessage) { }
    public record Event(Long id, Long actionId, String eventType, String fromStatus, String toStatus,
                        String payload, LocalDateTime occurredTime) { }
}
