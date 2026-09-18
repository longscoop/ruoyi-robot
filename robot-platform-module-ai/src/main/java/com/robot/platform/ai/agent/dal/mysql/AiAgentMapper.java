package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAgentMapper extends BaseMapperX<AiAgentDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_agent WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    AiAgentDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_agent WHERE code = #{code} AND tenant_id = #{tenantId} AND deleted = 0")
    AiAgentDO selectByCodeAndTenantId(@Param("code") String code, @Param("tenantId") long tenantId);
}
