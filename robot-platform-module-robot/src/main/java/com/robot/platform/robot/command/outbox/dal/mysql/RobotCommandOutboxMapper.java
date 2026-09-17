package com.robot.platform.robot.command.outbox.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RobotCommandOutboxMapper extends BaseMapperX<RobotCommandOutboxDO> {
    @TenantIgnore
    @Select("SELECT * FROM robot_command_outbox WHERE message_id=#{messageId} AND deleted=0")
    RobotCommandOutboxDO selectByMessageId(@Param("messageId") String messageId);
    /**
     * MySQL 8 SKIP LOCKED lets concurrent dispatchers make progress without waiting on a slow
     * worker. The caller changes each selected row to SENDING before releasing the transaction.
     */
    @TenantIgnore
    @Select("SELECT * FROM robot_command_outbox WHERE deleted=0 AND "
            + "((status IN ('PENDING','RETRY') AND next_attempt_time<=#{now}) "
            + "OR (status IN ('SENDING','PUBLISHING') AND claimed_at<=#{staleBefore})) "
            + "AND (message_type<>'MISSION_START' OR NOT EXISTS (SELECT 1 FROM robot_mission m "
            + "WHERE m.id=robot_command_outbox.mission_id AND m.tenant_id=robot_command_outbox.tenant_id "
            + "AND m.deleted=0 AND m.cancel_requested_time IS NOT NULL)) "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<RobotCommandOutboxDO> lockDueForDispatch(@Param("now") LocalDateTime now,
                                                   @Param("staleBefore") LocalDateTime staleBefore,
                                                   @Param("limit") int limit);

    /** claimed_at is a fencing token: only the worker that claimed this exact timestamp may send it. */
    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status='SENDING', claimed_at=#{claimedAt}, attempt_count=attempt_count+1, "
            + "last_error=NULL WHERE id=#{id} AND deleted=0 AND "
            + "((status IN ('PENDING','RETRY') AND next_attempt_time<=#{now}) "
            + "OR (status IN ('SENDING','PUBLISHING') AND claimed_at<=#{staleBefore})) "
            + "AND (message_type<>'MISSION_START' OR NOT EXISTS (SELECT 1 FROM robot_mission m "
            + "WHERE m.id=robot_command_outbox.mission_id AND m.tenant_id=robot_command_outbox.tenant_id "
            + "AND m.deleted=0 AND m.cancel_requested_time IS NOT NULL))")
    int markSending(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt,
                    @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status='SENT', claimed_at=NULL, last_error=NULL WHERE id=#{id} "
            + "AND status='PUBLISHING' AND claimed_at=#{claimedAt} AND deleted=0")
    int markSent(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt);

    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status=#{status}, claimed_at=NULL, next_attempt_time=#{nextAttemptTime}, "
            + "last_error=#{lastError} WHERE id=#{id} AND status='PUBLISHING' AND claimed_at=#{claimedAt} AND deleted=0")
    int markRetry(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt, @Param("status") String status,
                  @Param("nextAttemptTime") LocalDateTime nextAttemptTime, @Param("lastError") String lastError);

    /** Parsing and envelope validation fail before PUBLISHING, but still consume the claimed attempt. */
    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status=#{status}, claimed_at=NULL, next_attempt_time=#{nextAttemptTime}, "
            + "last_error=#{lastError} WHERE id=#{id} AND status='SENDING' AND claimed_at=#{claimedAt} AND deleted=0")
    int markPrePublishFailure(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt, @Param("status") String status,
                              @Param("nextAttemptTime") LocalDateTime nextAttemptTime, @Param("lastError") String lastError);

    /**
     * This is the cancellation half of the linearization point. It competes atomically with
     * {@link #beginPublishing(long, LocalDateTime)}: a successful update guarantees no broker
     * call can subsequently begin for this row.
     */
    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status='FAILED', claimed_at=NULL, last_error='MISSION_CANCELLED_BEFORE_DELIVERY' "
            + "WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} AND message_type='MISSION_START' "
            + "AND status IN ('PENDING','RETRY','SENDING') AND deleted=0")
    int invalidateUndeliveredStarts(@Param("tenantId") long tenantId, @Param("missionId") long missionId);

    /**
     * Broker I/O must only happen after this atomic transition. PUBLISHING means the worker is
     * authorized to make a broker call, not that the broker accepted or delivered it. The call
     * remains outside database locks; cancellation uses a device-side tombstone to make physical
     * MQTT reordering safe rather than pretending it can retract an in-flight call.
     */
    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status='PUBLISHING' WHERE id=#{id} AND status='SENDING' "
            + "AND claimed_at=#{claimedAt} AND deleted=0 "
            + "AND (message_type<>'MISSION_START' OR NOT EXISTS (SELECT 1 FROM robot_mission m "
            + "WHERE m.id=robot_command_outbox.mission_id AND m.tenant_id=robot_command_outbox.tenant_id "
            + "AND m.deleted=0 AND m.cancel_requested_time IS NOT NULL))")
    int beginPublishing(@Param("id") long id, @Param("claimedAt") LocalDateTime claimedAt);

    /** Records the compensation-required outcome without attempting to stop an in-flight publish. */
    @TenantIgnore
    @Update("UPDATE robot_command_outbox SET status='CANCEL_COMPENSATING', last_error='MISSION_CANCELLED_DURING_BROKER_PUBLISH' "
            + "WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} AND message_type='MISSION_START' "
            + "AND status='PUBLISHING' AND deleted=0")
    int markIrreversibleStartForCancellation(@Param("tenantId") long tenantId, @Param("missionId") long missionId);

    /** The immutable START envelope id becomes the device-side tombstone referenced by MISSION_CANCEL. */
    @TenantIgnore
    @Select("SELECT message_id FROM robot_command_outbox WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} "
            + "AND message_type='MISSION_START' AND deleted=0 ORDER BY id DESC LIMIT 1")
    String selectStartMessageId(@Param("tenantId") long tenantId, @Param("missionId") long missionId);
}
