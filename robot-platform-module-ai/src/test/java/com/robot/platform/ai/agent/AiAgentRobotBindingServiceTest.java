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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAgentRobotBindingServiceTest {

    @Mock private AiAgentMapper agentMapper;
    @Mock private AiAgentRobotMapper bindingMapper;
    @Mock private RobotOwnershipVerifier robotOwnershipVerifier;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void crossTenantAgentIsRejectedBeforeRobotCheck() {
        when(agentMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(null);
        var service = service();

        assertThrows(ServiceException.class, () -> service.bind(1L, 10L, 20L, false));

        verifyNoInteractions(robotOwnershipVerifier);
        verify(bindingMapper, never()).insert(any(AiAgentRobotDO.class));
    }

    @Test
    void crossTenantRobotIsRejectedByOwnershipVerifier() {
        when(agentMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(agent(10L, 1L, "assistant"));
        doThrow(new ServiceException(400, "invalid robot ownership"))
                .when(robotOwnershipVerifier).requireOwnedByTenant(1L, 20L);
        var service = service();

        assertThrows(ServiceException.class, () -> service.bind(1L, 10L, 20L, false));

        verify(bindingMapper, never()).insert(any(AiAgentRobotDO.class));
        verify(bindingMapper, never()).updateById(any(AiAgentRobotDO.class));
    }

    @Test
    void settingNewDefaultClearsPreviousDefaultBeforeUpsert() {
        when(agentMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(agent(10L, 1L, "assistant"));
        when(bindingMapper.selectBinding(1L, 20L, 10L)).thenReturn(null);
        doAnswer(invocation -> {
            AiAgentRobotDO row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(bindingMapper).insert(any(AiAgentRobotDO.class));
        var service = service();

        long bindingId = service.bind(1L, 10L, 20L, true);

        assertEquals(99L, bindingId);
        InOrder order = inOrder(bindingMapper);
        order.verify(bindingMapper).clearDefault(1L, 20L);
        order.verify(bindingMapper).selectBinding(1L, 20L, 10L);
        order.verify(bindingMapper).insert(argThat((AiAgentRobotDO row) ->
                row.getTenantId().equals(1L)
                        && row.getAgentId().equals(10L)
                        && row.getRobotId().equals(20L)
                        && Boolean.TRUE.equals(row.getIsDefault())
                        && "ENABLED".equals(row.getStatus())));
    }

    @Test
    void existingBindingIsReenabledAndUpdatedInsteadOfDuplicated() {
        when(agentMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(agent(10L, 1L, "assistant"));
        AiAgentRobotDO existing = binding(77L, 1L, 10L, 20L, false, "DISABLED");
        when(bindingMapper.selectBinding(1L, 20L, 10L)).thenReturn(existing);
        var service = service();

        long bindingId = service.bind(1L, 10L, 20L, false);

        assertEquals(77L, bindingId);
        assertEquals("ENABLED", existing.getStatus());
        assertFalse(existing.getIsDefault());
        verify(bindingMapper).updateById(existing);
        verify(bindingMapper, never()).insert(any(AiAgentRobotDO.class));
    }

    @Test
    void unboundAgentCodeIsRejected() {
        AiAgentDO agent = agent(10L, 1L, "assistant");
        when(agentMapper.selectByCodeAndTenantId("assistant", 1L)).thenReturn(agent);
        when(bindingMapper.selectBinding(1L, 20L, 10L)).thenReturn(null);
        var service = service();

        assertThrows(ServiceException.class,
                () -> service.requireAgentForRobot(1L, 20L, "assistant"));
    }

    @Test
    void disabledBindingIsNotUsable() {
        AiAgentDO agent = agent(10L, 1L, "assistant");
        when(agentMapper.selectByCodeAndTenantId("assistant", 1L)).thenReturn(agent);
        when(bindingMapper.selectBinding(1L, 20L, 10L))
                .thenReturn(binding(77L, 1L, 10L, 20L, false, "DISABLED"));
        var service = service();

        assertThrows(ServiceException.class,
                () -> service.requireAgentForRobot(1L, 20L, "assistant"));
    }

    @Test
    void defaultAgentIsResolvedWithinSameTenant() {
        when(bindingMapper.selectDefault(1L, 20L))
                .thenReturn(binding(77L, 1L, 10L, 20L, true, "ENABLED"));
        AiAgentDO agent = agent(10L, 1L, "assistant");
        when(agentMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(agent);
        var service = service();

        assertSame(agent, service.requireDefaultAgent(1L, 20L));

        verify(robotOwnershipVerifier).requireOwnedByTenant(1L, 20L);
        verify(agentMapper).selectByIdAndTenantId(10L, 1L);
    }

    @Test
    void tenantContextMismatchIsRejectedBeforeAccess() {
        TenantContextHolder.setTenantId(2L);
        var service = service();

        assertThrows(ServiceException.class, () -> service.bind(1L, 10L, 20L, false));

        verifyNoInteractions(agentMapper, bindingMapper, robotOwnershipVerifier);
    }

    private AiAgentRobotBindingService service() {
        return new AiAgentRobotBindingServiceImpl(agentMapper, bindingMapper, robotOwnershipVerifier);
    }

    private static AiAgentDO agent(long id, long tenantId, String code) {
        AiAgentDO row = new AiAgentDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName("Agent");
        row.setCode(code);
        row.setStatus("ENABLED");
        return row;
    }

    private static AiAgentRobotDO binding(long id, long tenantId, long agentId, long robotId,
                                          boolean isDefault, String status) {
        AiAgentRobotDO row = new AiAgentRobotDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setAgentId(agentId);
        row.setRobotId(robotId);
        row.setIsDefault(isDefault);
        row.setStatus(status);
        return row;
    }
}
