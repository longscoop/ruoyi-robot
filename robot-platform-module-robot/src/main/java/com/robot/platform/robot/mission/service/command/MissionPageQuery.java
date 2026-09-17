package com.robot.platform.robot.mission.service.command;

import com.robot.platform.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class MissionPageQuery extends PageParam {
    private Long robotId;
    private String status;
    private String source;
}
