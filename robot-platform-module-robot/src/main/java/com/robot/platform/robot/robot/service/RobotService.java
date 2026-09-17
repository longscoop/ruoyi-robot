package com.robot.platform.robot.robot.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.robot.robot.service.command.RobotPageQuery;
import com.robot.platform.robot.robot.service.command.RobotUpdateCommand;
import com.robot.platform.robot.robot.service.dto.RobotRespDTO;

public interface RobotService {
    long provision(RobotProvisionCommand command);
    void deprovision(long tenantId, long robotId);
    RobotRespDTO get(long id);
    PageResult<RobotRespDTO> page(RobotPageQuery query);
    void update(long id, RobotUpdateCommand command);
    void delete(long id);
}
