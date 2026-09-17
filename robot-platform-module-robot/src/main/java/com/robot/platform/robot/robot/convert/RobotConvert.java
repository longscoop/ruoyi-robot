package com.robot.platform.robot.robot.convert;

import com.robot.platform.robot.robot.controller.admin.vo.RobotCapabilityRespVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotPageReqVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotRespVO;
import com.robot.platform.robot.robot.controller.admin.vo.RobotUpdateReqVO;
import com.robot.platform.robot.robot.dal.dataobject.RobotCapabilityDO;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.service.command.RobotPageQuery;
import com.robot.platform.robot.robot.service.command.RobotUpdateCommand;
import com.robot.platform.robot.robot.service.dto.RobotRespDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RobotConvert {
    RobotConvert INSTANCE = Mappers.getMapper(RobotConvert.class);

    RobotRespDTO convert(RobotDO source);
    RobotRespVO convert(RobotRespDTO source);
    RobotUpdateCommand convert(RobotUpdateReqVO source);
    RobotPageQuery convert(RobotPageReqVO source);
    RobotCapabilityRespVO convert(RobotCapabilityDO source);
    List<RobotCapabilityRespVO> convertCapabilities(List<RobotCapabilityDO> source);
}
