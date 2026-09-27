package com.robot.platform.ai.digitalhuman.dal.mysql;

import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanActionDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiDigitalHumanActionMapper extends BaseMapperX<AiDigitalHumanActionDO> {
    @TenantIgnore
    @Select("SELECT * FROM ai_digital_human_action WHERE digital_human_id = #{digitalHumanId} AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id")
    List<AiDigitalHumanActionDO> selectByDigitalHumanIdAndTenantId(@Param("digitalHumanId") long digitalHumanId, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_digital_human_action WHERE digital_human_id = #{digitalHumanId} AND state = #{state} AND tenant_id = #{tenantId} AND deleted = 0")
    AiDigitalHumanActionDO selectByState(@Param("digitalHumanId") long digitalHumanId, @Param("state") String state, @Param("tenantId") long tenantId);

    @TenantIgnore
    @Update("UPDATE ai_digital_human_action SET deleted = 1 WHERE digital_human_id = #{digitalHumanId} AND tenant_id = #{tenantId} AND deleted = 0")
    int logicalDeleteByDigitalHumanIdAndTenantId(@Param("digitalHumanId") long digitalHumanId, @Param("tenantId") long tenantId);
}
