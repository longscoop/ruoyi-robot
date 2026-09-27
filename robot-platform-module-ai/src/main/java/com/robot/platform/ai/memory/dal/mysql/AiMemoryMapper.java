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
