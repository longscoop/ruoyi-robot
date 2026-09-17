package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelMapper extends BaseMapperX<AiModelDO> {
    default AiModelDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelDO>().eq(AiModelDO::getId, id).eq(AiModelDO::getTenantId, tenantId));
    }
    default AiModelDO selectByProviderAndCodeAndTenantId(long tenantId, long providerId, String modelCode) {
        return selectOne(new LambdaQueryWrapperX<AiModelDO>().eq(AiModelDO::getTenantId, tenantId).eq(AiModelDO::getProviderId, providerId).eq(AiModelDO::getModelCode, modelCode));
    }
}
