package com.robot.platform.robot.robot.controller.admin.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RobotCapabilityUpsertReqVO {
    @NotNull
    @Size(max = 4000)
    private String configuration;
}
