package com.robot.platform.ai.prompt.dal.mysql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiPromptMapper extends BaseMapperX<AiPromptDO> {

    default AiPromptDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(AiPromptDO::getId, id, AiPromptDO::getTenantId, tenantId);
    }

    default List<AiPromptDO> selectByCodeAndTenantId(String code, long tenantId) {
        return selectList(new LambdaQueryWrapper<AiPromptDO>()
                .eq(AiPromptDO::getTenantId, tenantId)
                .eq(AiPromptDO::getCode, code)
                .orderByAsc(AiPromptDO::getVersion));
    }

    default AiPromptDO selectLatestByCodeAndTenantId(String code, long tenantId) {
        return selectOne(new LambdaQueryWrapper<AiPromptDO>()
                .eq(AiPromptDO::getTenantId, tenantId)
                .eq(AiPromptDO::getCode, code)
                .orderByDesc(AiPromptDO::getVersion)
                .last("LIMIT 1"));
    }
}
