package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiModelProviderMapper extends BaseMapperX<AiModelProviderDO> {

    default AiModelProviderDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(AiModelProviderDO::getId, id, AiModelProviderDO::getTenantId, tenantId);
    }

    default AiModelProviderDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(AiModelProviderDO::getCode, code, AiModelProviderDO::getTenantId, tenantId);
    }

    default List<AiModelProviderDO> selectByTenantId(long tenantId) {
        return selectList(AiModelProviderDO::getTenantId, tenantId);
    }
}
