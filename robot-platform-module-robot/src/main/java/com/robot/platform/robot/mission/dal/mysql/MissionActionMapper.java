package com.robot.platform.robot.mission.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.robot.mission.dal.dataobject.MissionActionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MissionActionMapper extends BaseMapperX<MissionActionDO> {
    @Select("SELECT * FROM robot_mission_action WHERE id=#{actionId} AND tenant_id=#{tenantId} AND mission_id=#{missionId} AND deleted=0")
    MissionActionDO selectOwned(@Param("tenantId") long tenantId, @Param("missionId") long missionId, @Param("actionId") long actionId);

    /** A single aggregate query avoids completing a Mission from an incomplete in-memory action snapshot. */
    @Select("SELECT COUNT(*) FROM robot_mission_action WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} "
            + "AND deleted=0 AND status<>'SUCCESS'")
    long countNotSucceeded(@Param("tenantId") long tenantId, @Param("missionId") long missionId);

    @Select("SELECT * FROM robot_mission_action WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} AND deleted=0 ORDER BY sequence_no")
    List<MissionActionDO> selectByMission(@Param("tenantId") long tenantId, @Param("missionId") long missionId);

    /** The Mission row is locked by the service before this update, serializing progress reports. */
    @Update("UPDATE robot_mission_action SET status=#{status}, started_time=COALESCE(started_time, #{occurredAt}), "
            + "finished_time=CASE WHEN #{terminal} THEN #{occurredAt} ELSE finished_time END, error_code=#{errorCode}, error_message=#{errorMessage} "
            + "WHERE id=#{actionId} AND mission_id=#{missionId} AND tenant_id=#{tenantId} AND status=#{expectedStatus} AND deleted=0")
    int updateProgress(@Param("tenantId") long tenantId, @Param("missionId") long missionId, @Param("actionId") long actionId,
                       @Param("expectedStatus") String expectedStatus, @Param("status") String status, @Param("terminal") boolean terminal,
                       @Param("occurredAt") java.time.LocalDateTime occurredAt, @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);
}
