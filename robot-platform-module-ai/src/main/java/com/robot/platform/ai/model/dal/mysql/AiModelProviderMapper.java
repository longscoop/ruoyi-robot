package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiModelProviderMapper extends BaseMapperX<AiModelProviderDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_model_provider WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    AiModelProviderDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_model_provider WHERE code = #{code} AND tenant_id = #{tenantId} AND deleted = 0")
    AiModelProviderDO selectByCodeAndTenantId(@Param("code") String code, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_model_provider WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id")
    List<AiModelProviderDO> selectByTenantId(@Param("tenantId") long tenantId);

    @TenantIgnore
    @Update("UPDATE ai_model_provider SET deleted = 1 WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int logicalDeleteByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
}
