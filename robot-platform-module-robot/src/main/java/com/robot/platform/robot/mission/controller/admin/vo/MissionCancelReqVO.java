package com.robot.platform.robot.mission.controller.admin.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MissionCancelReqVO { @Size(max = 500) private String reason; }
