package com.robot.platform.ai.agent.controller.admin;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.agent.service.AiMissionSafetyPolicy;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.mission.controller.admin.vo.MissionCancelReqVO;
import com.robot.platform.robot.mission.controller.admin.vo.MissionCreateReqVO;
import com.robot.platform.robot.mission.service.MissionService;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCancelCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Authenticated cloud tool endpoint for external AI workflows. No provider can publish device commands here. */
@RestController
@RequestMapping("/ai/platform/agents/{agentId}/missions")
@RequiredArgsConstructor
public class AiAgentMissionController {
    private final AiAgentService agents;
    private final AiAgentRobotBindingService bindings;
    private final MissionService missions;
    private final AiMissionSafetyPolicy safety;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('robot:mission:create')")
    public CommonResult<MissionRespDTO> create(@PathVariable long agentId, @Valid @RequestBody MissionCreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        requireBound(tenantId, agentId, request.getRobotId());
        safety.validate(request.getActions());
        if (request.getRequestId().length() > 100) throw invalidParamException("AI mission requestId is too long");
        MissionCreateCommand command = new MissionCreateCommand();
        command.setRobotId(request.getRobotId());
        command.setMissionType(request.getMissionType());
        command.setRequestId(requestId(agentId, request.getRequestId()));
        command.setPriority(request.getPriority());
        command.setScheduledTime(request.getScheduledTime());
        command.setSource("AGENT");
        command.setCreatorId(getLoginUserId());
        command.setActions(request.getActions().stream()
                .map(action -> new MissionActionCommand(action.getActionType(), action.getParameters())).toList());
        return success(missions.create(command));
    }

    @GetMapping("/{missionId}")
    @PreAuthorize("@ss.hasPermission('robot:mission:query')")
    public CommonResult<MissionRespDTO> get(@PathVariable long agentId, @PathVariable long missionId) {
        return success(requireOwnedMission(agentId, missionId));
    }

    @PostMapping("/{missionId}/cancel")
    @PreAuthorize("@ss.hasPermission('robot:mission:cancel')")
    public CommonResult<Boolean> cancel(@PathVariable long agentId, @PathVariable long missionId,
                                         @Valid @RequestBody(required = false) MissionCancelReqVO request) {
        requireOwnedMission(agentId, missionId);
        MissionCancelCommand command = new MissionCancelCommand();
        if (request != null) command.setReason(request.getReason());
        missions.cancel(missionId, command);
        return success(true);
    }

    private MissionRespDTO requireOwnedMission(long agentId, long missionId) {
        MissionRespDTO mission = missions.get(missionId); // Tenant-filtered lookup is the authority for this ID.
        if (!"AGENT".equals(mission.source()) || !mission.requestId().startsWith("agent-" + agentId + ":")) {
            throw invalidParamException("Mission does not belong to this AI agent");
        }
        requireBound(TenantContextHolder.getRequiredTenantId(), agentId, mission.robotId());
        return mission;
    }

    private void requireBound(long tenantId, long agentId, long robotId) {
        AiAgentDO agent = agents.get(tenantId, agentId);
        if (!"ENABLED".equals(agent.getStatus())) throw invalidParamException("AI agent is disabled");
        bindings.requireAgentForRobot(tenantId, robotId, agent.getCode());
    }

    private static String requestId(long agentId, String externalId) {
        return "agent-" + agentId + ":" + externalId;
    }
}
