package com.robot.platform.robot.robot.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.robot.robot.dal.dataobject.RobotCapabilityDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RobotCapabilityMapper extends BaseMapperX<RobotCapabilityDO> {
    default RobotCapabilityDO selectByRobotIdAndCapabilityCode(long robotId, String capabilityCode) {
        return selectOne(RobotCapabilityDO::getRobotId, robotId,
                RobotCapabilityDO::getCapabilityCode, capabilityCode);
    }

    default List<RobotCapabilityDO> selectByRobotId(long robotId) {
        return selectList(RobotCapabilityDO::getRobotId, robotId);
    }

    @Delete("DELETE FROM robot_capability WHERE robot_id = #{robotId}")
    int physicalDeleteByRobotId(long robotId);
}
