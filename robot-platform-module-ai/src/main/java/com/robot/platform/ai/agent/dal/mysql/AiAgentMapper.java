package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAgentMapper extends BaseMapperX<AiAgentDO> {
    default AiAgentDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentDO>().eq(AiAgentDO::getId, id).eq(AiAgentDO::getTenantId, tenantId));
    }
    default AiAgentDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentDO>().eq(AiAgentDO::getCode, code).eq(AiAgentDO::getTenantId, tenantId));
    }
}
