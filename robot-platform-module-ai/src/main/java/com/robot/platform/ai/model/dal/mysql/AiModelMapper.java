package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiModelMapper extends BaseMapperX<AiModelDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_model WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    AiModelDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_model WHERE provider_id = #{providerId} AND model_code = #{modelCode} "
            + "AND tenant_id = #{tenantId} AND deleted = 0")
    AiModelDO selectByProviderAndCode(@Param("tenantId") long tenantId,
                                     @Param("providerId") long providerId,
                                     @Param("modelCode") String modelCode);

    @TenantIgnore
    @Select("SELECT * FROM ai_model WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id")
    List<AiModelDO> selectByTenantId(@Param("tenantId") long tenantId);

    @TenantIgnore
    @Update("UPDATE ai_model SET deleted = 1 WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int logicalDeleteByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
}
