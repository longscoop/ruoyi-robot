package com.robot.platform.robot.mission.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MissionActionReqVO {
    @NotBlank private String actionType;
    @NotBlank private String parameters;
}
