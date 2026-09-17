package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiModelMapper extends BaseMapperX<AiModelDO> {

    default AiModelDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(AiModelDO::getId, id, AiModelDO::getTenantId, tenantId);
    }

    default AiModelDO selectByProviderAndCode(long tenantId, long providerId, String modelCode) {
        return selectOne(AiModelDO::getTenantId, tenantId, AiModelDO::getProviderId, providerId,
                AiModelDO::getModelCode, modelCode);
    }

    default List<AiModelDO> selectByTenantId(long tenantId) {
        return selectList(AiModelDO::getTenantId, tenantId);
    }
}
