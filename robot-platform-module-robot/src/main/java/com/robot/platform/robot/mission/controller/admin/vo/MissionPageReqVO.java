package com.robot.platform.robot.mission.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class MissionPageReqVO extends PageParam {
    private Long robotId;
    private String status;
    private String source;
}
