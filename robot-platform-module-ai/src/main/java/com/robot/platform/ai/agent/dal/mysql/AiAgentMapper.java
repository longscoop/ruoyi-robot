package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiAgentMapper extends BaseMapperX<AiAgentDO> {

    default AiAgentDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(AiAgentDO::getId, id, AiAgentDO::getTenantId, tenantId);
    }

    default AiAgentDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(AiAgentDO::getCode, code, AiAgentDO::getTenantId, tenantId);
    }

    default List<AiAgentDO> selectByTenantId(long tenantId) {
        return selectList(AiAgentDO::getTenantId, tenantId);
    }
}
