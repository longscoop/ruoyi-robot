package com.robot.platform.ai.knowledge.dal.mysql;

import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeBaseDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiKnowledgeBaseMapper extends BaseMapperX<AiKnowledgeBaseDO> {
    @TenantIgnore
    @Select("SELECT * FROM ai_knowledge_base WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<AiKnowledgeBaseDO> selectOwnedList(@Param("tenantId") long tenantId);

    @TenantIgnore
    @Select("SELECT * FROM ai_knowledge_base WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    AiKnowledgeBaseDO selectOwned(@Param("tenantId") long tenantId, @Param("id") long id);

    @TenantIgnore
    @Update("UPDATE ai_knowledge_base SET deleted = 1 WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int logicalDeleteOwned(@Param("tenantId") long tenantId, @Param("id") long id);
}
