package com.robot.platform.ai.memory.dal.mysql;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiMemoryMapper extends BaseMapperX<AiMemoryDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_memory WHERE id = #{id} AND tenant_id = #{tenantId}")
    AiMemoryDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_memory WHERE tenant_id = #{tenantId} AND status != 'DELETED' ORDER BY id DESC")
    List<AiMemoryDO> selectAllByTenantId(@Param("tenantId") long tenantId);

    @TenantIgnore
    @Update("""
            UPDATE ai_memory
            SET content = #{content}, summary = #{summary}, importance = #{importance},
                expires_at = #{expiresAt}, updated_at = NOW(3)
            WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 'ACTIVE'
            """)
    int updateActiveByTenantId(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("content") String content, @Param("summary") String summary,
                               @Param("importance") java.math.BigDecimal importance,
                               @Param("expiresAt") LocalDateTime expiresAt);

    @TenantIgnore
    @Update("""
            UPDATE ai_memory SET status = 'DELETED', updated_at = NOW(3)
            WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 'ACTIVE'
            """)
    int invalidateActiveByTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("""
            <script>
            SELECT * FROM ai_memory
            WHERE tenant_id = #{tenantId}
              AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at &gt; #{now})
              AND (
                    (scope = 'ROBOT' AND robot_id = #{robotId})
                    <if test='memberId != null'>
                    OR (scope = 'MEMBER' AND member_id = #{memberId})
                    OR (scope = 'MEMBER_ROBOT' AND member_id = #{memberId} AND robot_id = #{robotId})
                    </if>
                  )
            </script>
            """)
    List<AiMemoryDO> selectActiveCandidates(@Param("tenantId") long tenantId,
                                             @Param("robotId") long robotId,
                                             @Param("memberId") Long memberId,
                                             @Param("now") LocalDateTime now);
}
