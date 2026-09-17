package com.robot.platform.robot.mission.controller.admin.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MissionRespVO {
    private Long id; private String missionNo; private Long robotId; private String missionType; private String source;
    private String status; private Integer priority; private String requestId; private LocalDateTime scheduledTime;
    private LocalDateTime startedTime; private LocalDateTime finishedTime; private String errorCode; private String errorMessage;
    /** Present on detail reads only; list responses deliberately stay compact. */
    private List<MissionActionVO> actions;
    /** Payloads are audit data, not credentials, and are returned only for the owned mission. */
    private List<MissionEventVO> events;

    @Data public static class MissionActionVO {
        private Long id; private Integer sequenceNo; private String actionType; private String parameters; private String status;
        private LocalDateTime startedTime; private LocalDateTime finishedTime; private String errorCode; private String errorMessage;
    }
    @Data public static class MissionEventVO {
        private Long id; private Long actionId; private String eventType; private String fromStatus; private String toStatus;
        private String payload; private LocalDateTime occurredTime;
    }
}
