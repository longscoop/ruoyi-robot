package com.robot.platform.ai.agent.dal.mysql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAgentRobotMapper extends BaseMapperX<AiAgentRobotDO> {

    default AiAgentRobotDO selectByTenantAgentAndRobot(long tenantId, long agentId, long robotId) {
        return selectOne(AiAgentRobotDO::getTenantId, tenantId, AiAgentRobotDO::getAgentId, agentId,
                AiAgentRobotDO::getRobotId, robotId);
    }

    default List<AiAgentRobotDO> selectByRobot(long tenantId, long robotId) {
        return selectList(AiAgentRobotDO::getTenantId, tenantId, AiAgentRobotDO::getRobotId, robotId);
    }

    default AiAgentRobotDO selectDefaultByRobot(long tenantId, long robotId) {
        return selectOne(new LambdaQueryWrapper<AiAgentRobotDO>()
                .eq(AiAgentRobotDO::getTenantId, tenantId)
                .eq(AiAgentRobotDO::getRobotId, robotId)
                .eq(AiAgentRobotDO::getIsDefault, true)
                .eq(AiAgentRobotDO::getStatus, "ENABLED")
                .last("LIMIT 1"));
    }

    @Update("UPDATE ai_agent_robot SET is_default = b'0', update_time = CURRENT_TIMESTAMP(3) " +
            "WHERE tenant_id = #{tenantId} AND robot_id = #{robotId} AND deleted = b'0'")
    int clearDefault(long tenantId, long robotId);
}
