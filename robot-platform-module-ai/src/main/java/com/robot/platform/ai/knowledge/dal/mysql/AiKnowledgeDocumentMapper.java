package com.robot.platform.ai.knowledge.dal.mysql;

import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeDocumentDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiKnowledgeDocumentMapper extends BaseMapperX<AiKnowledgeDocumentDO> {
    @TenantIgnore
    @Select("SELECT * FROM ai_knowledge_document WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND deleted = 0 ORDER BY id DESC")
    List<AiKnowledgeDocumentDO> selectOwnedList(@Param("tenantId") long tenantId, @Param("baseId") long baseId);

    @TenantIgnore
    @Select("SELECT * FROM ai_knowledge_document WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND id = #{id} AND deleted = 0")
    AiKnowledgeDocumentDO selectOwned(@Param("tenantId") long tenantId, @Param("baseId") long baseId, @Param("id") long id);

    @TenantIgnore
    @Select("SELECT COUNT(*) FROM ai_knowledge_document WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND deleted = 0")
    long countOwnedByBase(@Param("tenantId") long tenantId, @Param("baseId") long baseId);

    @TenantIgnore
    @Update("UPDATE ai_knowledge_document SET deleted = 1 WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND id = #{id} AND deleted = 0")
    int logicalDeleteOwned(@Param("tenantId") long tenantId, @Param("baseId") long baseId, @Param("id") long id);
}
