package com.robot.platform.ai.agent.service;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.agent.dal.mysql.AiAgentRobotMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.binding.service.RobotOwnershipVerifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiAgentRobotBindingServiceImpl implements AiAgentRobotBindingService {
    private static final String ENABLED = "ENABLED";

    private final AiAgentMapper agentMapper;
    private final AiAgentRobotMapper bindingMapper;
    private final RobotOwnershipVerifier robotOwnershipVerifier;

    public AiAgentRobotBindingServiceImpl(AiAgentMapper agentMapper,
                                          AiAgentRobotMapper bindingMapper,
                                          RobotOwnershipVerifier robotOwnershipVerifier) {
        this.agentMapper = agentMapper;
        this.bindingMapper = bindingMapper;
        this.robotOwnershipVerifier = robotOwnershipVerifier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long bind(long tenantId, long agentId, long robotId, boolean defaultAgent) {
        requireTenant(tenantId);
        requireOwnedAgent(tenantId, agentId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);

        if (defaultAgent) {
            bindingMapper.clearDefault(tenantId, robotId);
        }

        AiAgentRobotDO binding = bindingMapper.selectBinding(tenantId, robotId, agentId);
        if (binding == null) {
            binding = new AiAgentRobotDO();
            binding.setTenantId(tenantId);
            binding.setAgentId(agentId);
            binding.setRobotId(robotId);
            binding.setIsDefault(defaultAgent);
            binding.setStatus(ENABLED);
            try {
                bindingMapper.insert(binding);
            } catch (DuplicateKeyException exception) {
                throw invalidParamException("Agent is already bound to robot");
            }
        } else {
            binding.setIsDefault(defaultAgent);
            binding.setStatus(ENABLED);
            bindingMapper.updateById(binding);
        }
        return binding.getId();
    }

    @Override
    public AiAgentDO requireAgentForRobot(long tenantId, long robotId, String agentCode) {
        requireTenant(tenantId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);
        if (agentCode == null || agentCode.isBlank()) {
            throw invalidParamException("Agent code must not be blank");
        }

        AiAgentDO agent = agentMapper.selectByCodeAndTenantId(agentCode.trim(), tenantId);
        if (agent == null) {
            throw invalidParamException("AI agent does not exist in current tenant");
        }
        AiAgentRobotDO binding = bindingMapper.selectBinding(tenantId, robotId, agent.getId());
        if (binding == null || !ENABLED.equals(binding.getStatus())) {
            throw invalidParamException("Agent is not enabled for robot");
        }
        return agent;
    }

    @Override
    public AiAgentDO requireDefaultAgent(long tenantId, long robotId) {
        requireTenant(tenantId);
        robotOwnershipVerifier.requireOwnedByTenant(tenantId, robotId);

        AiAgentRobotDO binding = bindingMapper.selectDefault(tenantId, robotId);
        if (binding == null) {
            throw invalidParamException("Robot has no default AI agent");
        }
        return requireOwnedAgent(tenantId, binding.getAgentId());
    }

    private AiAgentDO requireOwnedAgent(long tenantId, long agentId) {
        AiAgentDO agent = agentMapper.selectByIdAndTenantId(agentId, tenantId);
        if (agent == null) {
            throw invalidParamException("AI agent does not exist in current tenant");
        }
        return agent;
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }
}
