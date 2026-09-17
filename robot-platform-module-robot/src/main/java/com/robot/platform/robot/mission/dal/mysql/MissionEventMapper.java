package com.robot.platform.robot.mission.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.robot.mission.dal.dataobject.MissionEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MissionEventMapper extends BaseMapperX<MissionEventDO> {
    @Select("SELECT * FROM robot_mission_event WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} "
            + "AND deleted=0 ORDER BY occurred_time, id")
    List<MissionEventDO> selectByMission(@Param("tenantId") long tenantId, @Param("missionId") long missionId);
}
