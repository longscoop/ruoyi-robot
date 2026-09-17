package com.robot.platform.ai.agent;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.agent.dal.mysql.AiAgentRobotMapper;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingServiceImpl;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.binding.service.RobotOwnershipVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentRobotBindingServiceTest {
    private final AiAgentMapper agentMapper = mock(AiAgentMapper.class);
    private final AiAgentRobotMapper bindingMapper = mock(AiAgentRobotMapper.class);
    private final RobotOwnershipVerifier robotOwnershipVerifier = mock(RobotOwnershipVerifier.class);
    private final AiAgentRobotBindingService service =
            new AiAgentRobotBindingServiceImpl(agentMapper, bindingMapper, robotOwnershipVerifier);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
        when(agentMapper.selectByIdAndTenantId(7L, 1L))
                .thenReturn(AiAgentDO.builder().id(7L).tenantId(1L).code("xiaoyou").build());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void makingDefaultClearsPreviousDefaultInsideBindingFlow() {
        AiAgentRobotDO existing = AiAgentRobotDO.builder().id(9L).tenantId(1L).agentId(7L).robotId(99L)
                .isDefault(false).status("ENABLED").build();
        when(bindingMapper.selectByTenantAgentAndRobot(1L, 7L, 99L)).thenReturn(existing);

        long id = service.bind(1L, 7L, 99L, true);

        assertThat(id).isEqualTo(9L);
        verify(robotOwnershipVerifier).requireOwnedByTenant(1L, 99L);
        verify(bindingMapper).clearDefault(1L, 99L);
        assertThat(existing.getIsDefault()).isTrue();
        verify(bindingMapper, atLeastOnce()).updateById(existing);
    }

    @Test
    void requireAgentForRobotRequiresEnabledTenantScopedBinding() {
        AiAgentDO agent = AiAgentDO.builder().id(7L).tenantId(1L).code("xiaoyou").build();
        when(agentMapper.selectByCodeAndTenantId("xiaoyou", 1L)).thenReturn(agent);
        when(bindingMapper.selectByTenantAgentAndRobot(1L, 7L, 99L))
                .thenReturn(AiAgentRobotDO.builder().id(9L).tenantId(1L).agentId(7L).robotId(99L)
                        .status("ENABLED").build());

        assertThat(service.requireAgentForRobot(1L, 99L, "xiaoyou")).isSameAs(agent);
        verify(robotOwnershipVerifier).requireOwnedByTenant(1L, 99L);
    }

    @Test
    void disabledBindingCannotResolveAgentForRobot() {
        when(agentMapper.selectByCodeAndTenantId("xiaoyou", 1L))
                .thenReturn(AiAgentDO.builder().id(7L).tenantId(1L).code("xiaoyou").build());
        when(bindingMapper.selectByTenantAgentAndRobot(1L, 7L, 99L))
                .thenReturn(AiAgentRobotDO.builder().id(9L).tenantId(1L).agentId(7L).robotId(99L)
                        .status("DISABLED").build());

        assertThatThrownBy(() -> service.requireAgentForRobot(1L, 99L, "xiaoyou"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsCrossTenantBeforeAnyOwnershipLookup() {
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> service.bind(1L, 7L, 99L, true)).isInstanceOf(ServiceException.class);

        verifyNoInteractions(robotOwnershipVerifier, bindingMapper);
    }
}
