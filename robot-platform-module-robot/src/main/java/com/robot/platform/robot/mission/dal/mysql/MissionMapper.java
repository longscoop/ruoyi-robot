package com.robot.platform.robot.mission.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.robot.platform.robot.mission.dal.dataobject.MissionDO;
import com.robot.platform.robot.mission.service.command.MissionPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import com.robot.platform.robot.mission.dal.dataobject.MissionDashboardTrendDO;

@Mapper
public interface MissionMapper extends BaseMapperX<MissionDO> {
    default MissionDO selectByRequestId(String requestId) { return selectOne(MissionDO::getRequestId, requestId); }

    /** Locking reads see a competing transaction's committed row even under MySQL REPEATABLE READ. */
    @Select("SELECT * FROM robot_mission WHERE tenant_id=#{tenantId} AND request_id=#{requestId} AND deleted=0 FOR SHARE")
    MissionDO selectCurrentByRequestId(@Param("tenantId") long tenantId, @Param("requestId") String requestId);

    @Select("SELECT * FROM robot_mission WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=0 FOR UPDATE")
    MissionDO selectCurrentById(@Param("tenantId") long tenantId, @Param("id") long id);

    default PageResult<MissionDO> selectPage(MissionPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<MissionDO>().eqIfPresent(MissionDO::getRobotId, query.getRobotId())
                .eqIfPresent(MissionDO::getStatus, query.getStatus()).eqIfPresent(MissionDO::getSource, query.getSource())
                .orderByDesc(MissionDO::getId));
    }

    @TenantIgnore
    @Select("SELECT COUNT(*) FROM robot_mission WHERE tenant_id=#{tenantId} AND deleted=0 "
            + "AND create_time>=#{start} AND create_time<#{end}")
    long countCreatedBetween(@Param("tenantId") long tenantId, @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @TenantIgnore
    @Select("SELECT COUNT(*) FROM robot_mission WHERE tenant_id=#{tenantId} AND deleted=0 AND status='FAILED' "
            + "AND create_time>=#{start} AND create_time<#{end}")
    long countFailedBetween(@Param("tenantId") long tenantId, @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    @TenantIgnore
    @Select("SELECT DATE(create_time) AS mission_date, COUNT(*) AS total, "
            + "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed "
            + "FROM robot_mission WHERE tenant_id=#{tenantId} AND deleted=0 "
            + "AND create_time>=#{startDate} AND create_time<DATE_ADD(#{endDate}, INTERVAL 1 DAY) "
            + "GROUP BY DATE(create_time) ORDER BY mission_date")
    List<MissionDashboardTrendDO> selectDashboardTrend(@Param("tenantId") long tenantId,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    /** Compare-and-swap guards cancellation, timeout, and later device-event transitions. */
    @Update("UPDATE robot_mission SET status=#{target}, started_time=#{startedTime}, finished_time=#{finishedTime}, "
            + "error_code=#{errorCode}, error_message=#{errorMessage}, version=version+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND status=#{expected} AND version=#{version} AND deleted=0")
    int transitionIfVersion(@Param("id") long id, @Param("tenantId") long tenantId, @Param("expected") String expected,
                            @Param("target") String target, @Param("version") int version,
                            @Param("startedTime") LocalDateTime startedTime, @Param("finishedTime") LocalDateTime finishedTime,
                            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    @Update("UPDATE robot_mission SET cancel_requested_time=#{requestedAt}, version=version+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND status IN ('DISPATCHED','RUNNING','PAUSED') "
            + "AND version=#{version} AND deleted=0 AND cancel_requested_time IS NULL")
    int requestCancelIfVersion(@Param("id") long id, @Param("tenantId") long tenantId, @Param("version") int version,
                               @Param("requestedAt") LocalDateTime requestedAt);

    @Select("SELECT id FROM robot_mission WHERE tenant_id=#{tenantId} AND robot_id=#{robotId} "
            + "AND status='PENDING' AND deleted=0 AND create_time<=#{cutoff}")
    java.util.List<Long> selectPendingIdsBefore(@Param("tenantId") long tenantId, @Param("robotId") long robotId,
                                                 @Param("cutoff") LocalDateTime cutoff);

    /** Job discovery must not inherit the scheduler's tenant; each row is re-entered in its owner context. */
    @TenantIgnore
    @Select("SELECT * FROM robot_mission WHERE status='PENDING' AND create_time<=#{cutoff} AND deleted=0")
    java.util.List<MissionDO> selectExpiredPendingIgnoringTenant(@Param("cutoff") LocalDateTime cutoff);

    /** Platform scheduler only discovers rows; each id is dispatched through MissionService in its tenant context. */
    @TenantIgnore
    @Select("SELECT * FROM robot_mission WHERE status='PENDING' AND deleted=0 ORDER BY priority DESC, create_time ASC LIMIT #{limit}")
    java.util.List<MissionDO> selectPendingForDispatchIgnoringTenant(@Param("limit") int limit);
}
