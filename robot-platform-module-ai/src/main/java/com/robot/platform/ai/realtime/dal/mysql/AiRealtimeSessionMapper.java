package com.robot.platform.ai.realtime.dal.mysql;

import com.robot.platform.ai.realtime.dal.dataobject.AiRealtimeSessionDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiRealtimeSessionMapper extends BaseMapperX<AiRealtimeSessionDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_realtime_session WHERE id = #{id} AND tenant_id = #{tenantId}")
    AiRealtimeSessionDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
}
