package com.robot.platform.ai.digitalhuman.dal.mysql;

import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiDigitalHumanMapper extends BaseMapperX<AiDigitalHumanDO> {
    @TenantIgnore
    @Select("SELECT * FROM ai_digital_human WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    AiDigitalHumanDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_digital_human WHERE code = #{code} AND tenant_id = #{tenantId} AND deleted = 0")
    AiDigitalHumanDO selectByCodeAndTenantId(@Param("code") String code, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_digital_human WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id")
    List<AiDigitalHumanDO> selectByTenantId(@Param("tenantId") long tenantId);

    @TenantIgnore
    @Update("UPDATE ai_digital_human SET deleted = 1 WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int logicalDeleteByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
}
