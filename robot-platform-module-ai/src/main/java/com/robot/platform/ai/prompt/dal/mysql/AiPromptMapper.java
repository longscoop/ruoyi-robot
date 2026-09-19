package com.robot.platform.ai.prompt.dal.mysql;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiPromptMapper extends BaseMapperX<AiPromptDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_prompt WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    AiPromptDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_prompt WHERE code = #{code} AND tenant_id = #{tenantId} AND deleted = 0 "
            + "ORDER BY version DESC LIMIT 1")
    AiPromptDO selectLatestByCodeAndTenantId(@Param("code") String code, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_prompt WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY code, version DESC, id")
    List<AiPromptDO> selectByTenantId(@Param("tenantId") long tenantId);
}
