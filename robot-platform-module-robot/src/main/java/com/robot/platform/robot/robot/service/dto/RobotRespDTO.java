package com.robot.platform.robot.robot.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RobotRespDTO {
    private Long id;
    private Long deviceId;
    private Long productId;
    private String robotCode;
    private String name;
    private String onlineStatus;
    private String workStatus;
    private Integer batteryLevel;
    private LocalDateTime createTime;
}
