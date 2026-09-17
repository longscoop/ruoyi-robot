package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AiAgentRobotMapper extends BaseMapperX<AiAgentRobotDO> {
    default AiAgentRobotDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getId, id).eq(AiAgentRobotDO::getTenantId, tenantId));
    }
    default List<AiAgentRobotDO> selectByRobot(long tenantId, long robotId) {
        return selectList(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getTenantId, tenantId).eq(AiAgentRobotDO::getRobotId, robotId));
    }
    default List<AiAgentRobotDO> selectByAgent(long tenantId, long agentId) {
        return selectList(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getTenantId, tenantId).eq(AiAgentRobotDO::getAgentId, agentId));
    }
}
