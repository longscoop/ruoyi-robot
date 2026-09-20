package com.robot.platform.ai.memory.dal.mysql;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiMemoryMapper extends BaseMapperX<AiMemoryDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_memory WHERE id = #{id} AND tenant_id = #{tenantId}")
    AiMemoryDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
}
