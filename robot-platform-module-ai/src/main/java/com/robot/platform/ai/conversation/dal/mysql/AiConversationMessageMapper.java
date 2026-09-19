package com.robot.platform.ai.conversation.dal.mysql;

import com.robot.platform.ai.conversation.dal.dataobject.AiConversationMessageDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiConversationMessageMapper extends BaseMapperX<AiConversationMessageDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_conversation_message "
            + "WHERE tenant_id = #{tenantId} AND conversation_id = #{conversationId} "
            + "ORDER BY conversation_id, created_at, id")
    List<AiConversationMessageDO> selectByConversationIdAndTenantId(
            @Param("conversationId") long conversationId,
            @Param("tenantId") long tenantId);
}
