package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelProviderMapper extends BaseMapperX<AiModelProviderDO> {
    default AiModelProviderDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelProviderDO>().eq(AiModelProviderDO::getId, id).eq(AiModelProviderDO::getTenantId, tenantId));
    }
    default AiModelProviderDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelProviderDO>().eq(AiModelProviderDO::getCode, code).eq(AiModelProviderDO::getTenantId, tenantId));
    }
}
