package com.robot.platform.robot.robot.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RobotUpdateReqVO {
    @NotBlank
    @Size(max = 128)
    private String name;
}
