package com.robot.platform.ai.agent;

import com.robot.platform.ai.agent.controller.admin.AiAgentMissionController;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.agent.service.AiMissionSafetyPolicy;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.mission.controller.admin.vo.MissionActionReqVO;
import com.robot.platform.robot.mission.controller.admin.vo.MissionCreateReqVO;
import com.robot.platform.robot.mission.enums.MissionStatus;
import com.robot.platform.robot.mission.service.MissionService;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAgentMissionControllerTest {
    private final AiAgentService agents = mock(AiAgentService.class);
    private final AiAgentRobotBindingService bindings = mock(AiAgentRobotBindingService.class);
    private final MissionService missions = mock(MissionService.class);
    private final AiAgentMissionController controller = new AiAgentMissionController(agents, bindings, missions, new AiMissionSafetyPolicy());

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void createsOnlyBoundAgentMissionViaMissionService() {
        AiAgentDO agent = new AiAgentDO();
        agent.setCode("guide"); agent.setStatus("ENABLED");
        when(agents.get(7L, 12L)).thenReturn(agent);
        MissionCreateReqVO request = new MissionCreateReqVO();
        request.setRobotId(44L); request.setMissionType("PATROL"); request.setRequestId("turn-1");
        request.setPriority(5);
        MissionActionReqVO action = new MissionActionReqVO();
        action.setActionType("RETURN_HOME"); action.setParameters("{}");
        request.setActions(List.of(action));

        controller.create(12L, request);

        verify(bindings).requireAgentForRobot(7L, 44L, "guide");
        ArgumentCaptor<MissionCreateCommand> captured = ArgumentCaptor.forClass(MissionCreateCommand.class);
        verify(missions).create(captured.capture());
        assertEquals("AGENT", captured.getValue().getSource());
        assertEquals("agent-12:turn-1", captured.getValue().getRequestId());
        assertEquals("RETURN_HOME", captured.getValue().getActions().get(0).actionType());
    }

    @Test
    void rejectsOtherAgentsMissionBeforeCancellation() {
        when(missions.get(99L)).thenReturn(new MissionRespDTO(99L, "m99", 44L, "PATROL", "AGENT",
                MissionStatus.PENDING, 5, "agent-13:turn-1", null, null, null, null, null));
        assertThrows(RuntimeException.class, () -> controller.cancel(12L, 99L, null));
        verify(missions, never()).cancel(anyLong(), any());
        verifyNoInteractions(bindings);
    }

    @Test
    void disabledAgentCannotCreateMission() {
        AiAgentDO agent = new AiAgentDO();
        agent.setCode("guide"); agent.setStatus("DISABLED");
        when(agents.get(7L, 12L)).thenReturn(agent);
        MissionCreateReqVO request = new MissionCreateReqVO(); request.setRobotId(44L);
        assertThrows(RuntimeException.class, () -> controller.create(12L, request));
        verifyNoInteractions(bindings, missions);
    }
}
