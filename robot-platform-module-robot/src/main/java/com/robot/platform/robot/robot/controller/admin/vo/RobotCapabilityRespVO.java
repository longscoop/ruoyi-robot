package com.robot.platform.robot.robot.controller.admin.vo;

import lombok.Data;

@Data
public class RobotCapabilityRespVO {
    private Long id;
    private Long robotId;
    private String capabilityCode;
    private String configuration;
}
