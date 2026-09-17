package com.robot.platform.robot.robot.service.command;

import com.robot.platform.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class RobotPageQuery extends PageParam {
    private String robotCode;
    private String name;
    private String onlineStatus;
    private String workStatus;
}
