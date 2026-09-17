package com.robot.platform.robot.robot.controller.admin.vo;

import com.robot.platform.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class RobotPageReqVO extends PageParam {
    private String robotCode;
    private String name;
    private String onlineStatus;
    private String workStatus;
}
