package com.robot.platform.robot.mission.service.command;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MissionCreateCommand {
    private Long robotId;
    private String missionType;
    private String source;
    private Integer priority;
    private String requestId;
    private Long creatorId;
    private LocalDateTime scheduledTime;
    private List<MissionActionCommand> actions;
}
