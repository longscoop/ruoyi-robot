package com.robot.platform.robot.realtime.outbox.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.robot.realtime.outbox.dal.dataobject.RobotRealtimeEventOutboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RobotRealtimeEventOutboxMapper extends BaseMapperX<RobotRealtimeEventOutboxDO> {
    @TenantIgnore
    @Select("SELECT id FROM robot_realtime_event_outbox WHERE tenant_id=#{tenantId} AND event_key=#{eventKey} AND deleted=0")
    Long selectIdByEventKey(@Param("tenantId") long tenantId, @Param("eventKey") String eventKey);

    @TenantIgnore
    @Update("UPDATE robot_realtime_event_outbox SET status='SENDING', attempt_count=attempt_count+1, claimed_at=#{now} "
            + "WHERE id=#{id} AND deleted=0 AND ((status IN ('PENDING','RETRY') AND next_attempt_time<=#{now}) "
            + "OR (status='SENDING' AND claimed_at<=#{staleBefore}))")
    int claim(@Param("id") long id, @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore);

    @TenantIgnore
    @Update("UPDATE robot_realtime_event_outbox SET status='SENT', claimed_at=NULL, last_error=NULL "
            + "WHERE id=#{id} AND deleted=0 AND status='SENDING' AND claimed_at=#{now}")
    int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

    @TenantIgnore
    @Update("UPDATE robot_realtime_event_outbox SET status='RETRY', next_attempt_time=#{nextAttempt}, claimed_at=NULL, "
            + "last_error=#{lastError} WHERE id=#{id} AND deleted=0 AND status='SENDING' AND claimed_at=#{claimedAt}")
    int markRetry(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt, @Param("nextAttempt") LocalDateTime nextAttempt,
                  @Param("lastError") String lastError);

    @TenantIgnore
    @Select("SELECT id FROM robot_realtime_event_outbox WHERE deleted=0 AND ((status IN ('PENDING','RETRY') "
            + "AND next_attempt_time<=#{now}) OR (status='SENDING' AND claimed_at<=#{staleBefore})) ORDER BY id LIMIT #{limit}")
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore,
                            @Param("limit") int limit);

    @TenantIgnore
    @Select("SELECT * FROM robot_realtime_event_outbox WHERE id=#{id} AND deleted=0")
    RobotRealtimeEventOutboxDO selectByIdIgnoringTenant(@Param("id") long id);
}
