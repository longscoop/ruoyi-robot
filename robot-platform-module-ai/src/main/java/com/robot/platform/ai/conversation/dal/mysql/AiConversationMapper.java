package com.robot.platform.ai.conversation.dal.mysql;

import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AiConversationMapper extends BaseMapperX<AiConversationDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_conversation WHERE id = #{id} AND tenant_id = #{tenantId}")
    AiConversationDO selectByIdAndTenantId(@Param("id") long id, @Param("tenantId") long tenantId);
    @TenantIgnore
    @Select("SELECT * FROM ai_conversation WHERE tenant_id = #{tenantId} ORDER BY created_at DESC")
    List<AiConversationDO> selectAllByTenantId(@Param("tenantId") long tenantId);
}
