package com.robot.platform.ai.prompt.dal.mysql;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPromptMapper extends BaseMapperX<AiPromptDO> {
    default AiPromptDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiPromptDO>().eq(AiPromptDO::getId, id).eq(AiPromptDO::getTenantId, tenantId));
    }
    default AiPromptDO selectByCodeAndVersionAndTenantId(String code, Integer version, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiPromptDO>().eq(AiPromptDO::getCode, code).eq(AiPromptDO::getVersion, version).eq(AiPromptDO::getTenantId, tenantId));
    }
}
