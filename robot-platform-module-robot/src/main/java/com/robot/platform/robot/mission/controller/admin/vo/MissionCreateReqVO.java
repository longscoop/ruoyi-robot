package com.robot.platform.robot.mission.controller.admin.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MissionCreateReqVO {
    @NotNull private Long robotId;
    @NotBlank private String missionType;
    @NotBlank private String requestId;
    @NotNull @Min(0) @Max(100) private Integer priority;
    private LocalDateTime scheduledTime;
    @NotEmpty @Valid private List<MissionActionReqVO> actions;
}
