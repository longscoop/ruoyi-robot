package com.robot.platform.robot.robot.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RobotRespVO {
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
