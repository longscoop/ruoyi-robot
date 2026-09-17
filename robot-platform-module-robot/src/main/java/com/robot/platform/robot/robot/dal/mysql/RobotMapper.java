package com.robot.platform.robot.robot.dal.mysql;

import com.robot.platform.framework.common.pojo.PageResult;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.service.command.RobotPageQuery;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;

@Mapper
public interface RobotMapper extends BaseMapperX<RobotDO> {
    default RobotDO selectByDeviceId(long deviceId) {
        return selectOne(RobotDO::getDeviceId, deviceId);
    }

    @TenantIgnore
    @Select("SELECT * FROM robot WHERE tenant_id = #{tenantId} AND device_id = #{deviceId} AND deleted = 0")
    RobotDO selectByTenantAndDeviceId(@Param("tenantId") long tenantId, @Param("deviceId") long deviceId);

    @TenantIgnore
    @Select("SELECT * FROM robot WHERE tenant_id = #{tenantId} AND id = #{robotId} AND deleted = 0")
    RobotDO selectByTenantAndId(@Param("tenantId") long tenantId, @Param("robotId") long robotId);

    /**
     * Serializes heartbeat and offline transitions for one robot. Both writers take this InnoDB
     * row lock before they read or mutate the Redis projection, so neither can derive an event
     * from a pre-transition snapshot.
     */
    @TenantIgnore
    @Select("SELECT * FROM robot WHERE tenant_id = #{tenantId} AND id = #{robotId} AND deleted = 0 FOR UPDATE")
    RobotDO selectByTenantAndIdForUpdate(@Param("tenantId") long tenantId, @Param("robotId") long robotId);

    default PageResult<RobotDO> selectPage(RobotPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<RobotDO>()
                .likeIfPresent(RobotDO::getRobotCode, query.getRobotCode())
                .likeIfPresent(RobotDO::getName, query.getName())
                .eqIfPresent(RobotDO::getOnlineStatus, query.getOnlineStatus())
                .eqIfPresent(RobotDO::getWorkStatus, query.getWorkStatus())
                .orderByDesc(RobotDO::getId));
    }

    /** Dashboard aggregation must state the owner tenant explicitly, outside any caller context. */
    @TenantIgnore
    @Select("SELECT * FROM robot WHERE tenant_id=#{tenantId} AND deleted=0 ORDER BY id DESC")
    java.util.List<RobotDO> selectDashboardRobots(@Param("tenantId") long tenantId);

    /** Uses MySQL's durable count instead of deriving a summary from the visible row slice. */
    @TenantIgnore
    @Select("SELECT COUNT(*) FROM robot WHERE tenant_id=#{tenantId} AND deleted=0")
    long countDashboardRobots(@Param("tenantId") long tenantId);

    /** Physical removal releases the database's globally unique device binding for reactivation. */
    @Delete("DELETE FROM robot WHERE id = #{id}")
    int physicalDeleteById(long id);

    default int updateName(long id, String name) {
        RobotDO update = new RobotDO();
        update.setId(id);
        update.setName(name);
        return updateById(update);
    }

    /** Platform-scoped candidate discovery; callers must enter each returned tenant before use. */
    @TenantIgnore
    @Select("SELECT * FROM robot WHERE online_status = 'ONLINE' AND deleted = 0")
    java.util.List<RobotDO> selectOnlineCandidatesIgnoringTenant();

    /** The DB condition is the durable race guard when Redis has expired or restarted. */
    @TenantIgnore
    @Update("UPDATE robot SET online_status = 'OFFLINE' WHERE id = #{robotId} AND tenant_id = #{tenantId} "
            + "AND online_status = 'ONLINE' AND (last_heartbeat_time IS NULL OR last_heartbeat_time <= #{cutoff})")
    int markOfflineIfHeartbeatBefore(@Param("tenantId") long tenantId, @Param("robotId") long robotId,
                                     @Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * Writes the recovery snapshot for a row that the caller already locked with
     * {@link #selectByTenantAndIdForUpdate(long, long)}. This intentionally bypasses the
     * entity-wide null-field update policy: a heartbeat which omits mission/version must clear
     * the durable values rather than leave stale telemetry behind.
     */
    @TenantIgnore
    @Update("UPDATE robot SET online_status = #{onlineStatus}, work_status = #{workStatus}, "
            + "battery_level = #{batteryLevel}, ip_address = #{ipAddress}, "
            + "current_mission_id = #{currentMissionId}, software_version = #{softwareVersion}, "
            + "last_heartbeat_time = #{lastHeartbeatTime} "
            + "WHERE id = #{robotId} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateHeartbeatSnapshotForLockedRobot(@Param("tenantId") long tenantId, @Param("robotId") long robotId,
            @Param("onlineStatus") String onlineStatus, @Param("workStatus") String workStatus,
            @Param("batteryLevel") Integer batteryLevel, @Param("ipAddress") String ipAddress,
            @Param("currentMissionId") String currentMissionId, @Param("softwareVersion") String softwareVersion,
            @Param("lastHeartbeatTime") java.time.LocalDateTime lastHeartbeatTime);
}
