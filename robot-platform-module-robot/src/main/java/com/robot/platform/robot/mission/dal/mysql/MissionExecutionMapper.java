package com.robot.platform.robot.mission.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.robot.mission.dal.dataobject.MissionExecutionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MissionExecutionMapper extends BaseMapperX<MissionExecutionDO> {
    /** Command message id is unique, so redelivery records one durable ACK fact. */
    @Insert("INSERT INTO robot_mission_execution (tenant_id, mission_id, attempt_no, command_message_id, command_type, acknowledged_time, result) "
            // result is a MySQL JSON column: bind status through JSON_OBJECT rather than relying
            // on a JDBC string to be valid JSON (plain ACCEPTED is not a JSON document).
            + "VALUES (#{tenantId}, #{missionId}, #{attemptNo}, #{commandMessageId}, #{commandType}, #{acknowledgedTime}, JSON_OBJECT('status', #{result})) "
            + "ON DUPLICATE KEY UPDATE acknowledged_time=VALUES(acknowledged_time), result=VALUES(result)")
    int upsertAcknowledgement(@Param("tenantId") long tenantId, @Param("missionId") long missionId,
                              @Param("attemptNo") int attemptNo, @Param("commandMessageId") String commandMessageId,
                              @Param("commandType") String commandType, @Param("acknowledgedTime") LocalDateTime acknowledgedTime,
                              @Param("result") String result);
}
