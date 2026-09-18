package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiAgentRobotMapper extends BaseMapperX<AiAgentRobotDO> {

    @TenantIgnore
    @Select("SELECT * FROM ai_agent_robot WHERE tenant_id = #{tenantId} AND robot_id = #{robotId} "
            + "AND deleted = 0 ORDER BY id")
    List<AiAgentRobotDO> selectByRobot(@Param("tenantId") long tenantId, @Param("robotId") long robotId);

    @TenantIgnore
    @Select("SELECT * FROM ai_agent_robot WHERE tenant_id = #{tenantId} AND agent_id = #{agentId} "
            + "AND deleted = 0 ORDER BY id")
    List<AiAgentRobotDO> selectByAgent(@Param("tenantId") long tenantId, @Param("agentId") long agentId);

    @TenantIgnore
    @Select("SELECT * FROM ai_agent_robot WHERE tenant_id = #{tenantId} AND robot_id = #{robotId} "
            + "AND agent_id = #{agentId} AND deleted = 0")
    AiAgentRobotDO selectBinding(@Param("tenantId") long tenantId,
                                 @Param("robotId") long robotId,
                                 @Param("agentId") long agentId);
}
