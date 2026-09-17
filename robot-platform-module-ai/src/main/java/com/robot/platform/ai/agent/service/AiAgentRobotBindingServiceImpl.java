package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.agent.dal.mysql.AiAgentRobotMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.binding.service.RobotOwnershipVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.robot.platform.ai.enums.AiErrorCodeConstants.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class AiAgentRobotBindingServiceImpl implements AiAgentRobotBindingService {
    private final AiAgentMapper agentMapper;
    private final AiAgentRobotMapper bindingMapper;
    private final RobotOwnershipVerifier robotOwnershipVerifier;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long bind(long tenantId, long agentId, long robotId, boolean makeDefault) {
        requireCurrentTenant(tenantId);
        requireAgent(tenantId, agentId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);

        AiAgentRobotDO binding = bindingMapper.selectByTenantAgentAndRobot(tenantId, agentId, robotId);
        if (binding == null) {
            binding = AiAgentRobotDO.builder()
                    .tenantId(tenantId)
                    .agentId(agentId)
                    .robotId(robotId)
                    .isDefault(false)
                    .status("ENABLED")
                    .build();
            bindingMapper.insert(binding);
        } else {
            binding.setStatus("ENABLED");
            bindingMapper.updateById(binding);
        }
        if (makeDefault) {
            switchDefault(tenantId, robotId, binding);
        }
        return binding.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(long tenantId, long agentId, long robotId) {
        requireCurrentTenant(tenantId);
        requireAgent(tenantId, agentId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        AiAgentRobotDO binding = requireBinding(tenantId, agentId, robotId);
        binding.setStatus("DISABLED");
        binding.setIsDefault(false);
        bindingMapper.updateById(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(long tenantId, long agentId, long robotId) {
        requireCurrentTenant(tenantId);
        requireAgent(tenantId, agentId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        AiAgentRobotDO binding = requireBinding(tenantId, agentId, robotId);
        if (!"ENABLED".equals(binding.getStatus())) {
            throw exception(AI_AGENT_ROBOT_NOT_BOUND);
        }
        switchDefault(tenantId, robotId, binding);
    }

    @Override
    public List<AiAgentRobotDO> listByRobot(long tenantId, long robotId) {
        requireCurrentTenant(tenantId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        return bindingMapper.selectByRobot(tenantId, robotId);
    }

    @Override
    public AiAgentRobotDO getDefault(long tenantId, long robotId) {
        requireCurrentTenant(tenantId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        return bindingMapper.selectDefaultByRobot(tenantId, robotId);
    }

    @Override
    public AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode) {
        requireCurrentTenant(tenantId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        AiAgentDO agent = agentMapper.selectByCodeAndTenantId(agentCode, tenantId);
        if (agent == null) {
            throw exception(AI_AGENT_NOT_EXISTS);
        }
        AiAgentRobotDO binding = bindingMapper.selectByTenantAgentAndRobot(tenantId, agent.getId(), robotId);
        if (binding == null || !"ENABLED".equals(binding.getStatus())) {
            throw exception(AI_AGENT_ROBOT_NOT_BOUND);
        }
        return agent;
    }

    private void switchDefault(long tenantId, long robotId, AiAgentRobotDO binding) {
        bindingMapper.clearDefault(tenantId, robotId);
        binding.setIsDefault(true);
        binding.setStatus("ENABLED");
        bindingMapper.updateById(binding);
    }

    private AiAgentRobotDO requireBinding(long tenantId, long agentId, long robotId) {
        AiAgentRobotDO binding = bindingMapper.selectByTenantAgentAndRobot(tenantId, agentId, robotId);
        if (binding == null) {
            throw exception(AI_AGENT_ROBOT_NOT_BOUND);
        }
        return binding;
    }

    private AiAgentDO requireAgent(long tenantId, long agentId) {
        AiAgentDO agent = agentMapper.selectByIdAndTenantId(agentId, tenantId);
        if (agent == null) {
            throw exception(AI_AGENT_NOT_EXISTS);
        }
        return agent;
    }

    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) {
            throw exception(AI_TENANT_FORBIDDEN);
        }
    }
}
