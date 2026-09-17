package com.robot.platform.robot.mission.service.command;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class MissionPageQuery extends PageParam {
    private Long robotId;
    private String status;
    private String source;
}
