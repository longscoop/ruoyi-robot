package com.robot.platform.robot.mission.controller.admin;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.common.pojo.PageResult;
import com.robot.platform.robot.mission.controller.admin.vo.*;
import com.robot.platform.robot.mission.service.MissionService;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCancelCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.command.MissionPageQuery;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import com.robot.platform.robot.mission.service.dto.MissionDetailDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Admin routes depend only on MissionService, preserving mapper and transport boundaries. */
@Tag(name = "管理后台 - 任务")
@RestController
@RequestMapping("/admin-api/robot/missions")
@Validated
@RequiredArgsConstructor
public class MissionController {
    private final MissionService missions;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('robot:mission:create')")
    public CommonResult<MissionRespVO> create(@Valid @RequestBody MissionCreateReqVO request) {
        MissionCreateCommand command = new MissionCreateCommand();
        command.setRobotId(request.getRobotId()); command.setMissionType(request.getMissionType()); command.setRequestId(request.getRequestId());
        command.setPriority(request.getPriority()); command.setScheduledTime(request.getScheduledTime()); command.setSource("ADMIN");
        command.setCreatorId(getLoginUserId());
        command.setActions(request.getActions().stream().map(action -> new MissionActionCommand(action.getActionType(), action.getParameters())).toList());
        return success(response(missions.create(command)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('robot:mission:query')")
    public CommonResult<MissionRespVO> get(@PathVariable long id) { return success(detailResponse(missions.detail(id))); }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('robot:mission:query')")
    public CommonResult<PageResult<MissionRespVO>> page(@Valid MissionPageReqVO request) {
        MissionPageQuery query = new MissionPageQuery(); query.setPageNo(request.getPageNo()); query.setPageSize(request.getPageSize());
        query.setRobotId(request.getRobotId()); query.setStatus(request.getStatus()); query.setSource(request.getSource());
        PageResult<MissionRespDTO> page = missions.page(query);
        return success(new PageResult<>(page.getList().stream().map(MissionController::response).toList(), page.getTotal()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('robot:mission:cancel')")
    public CommonResult<Boolean> cancel(@PathVariable long id, @Valid @RequestBody(required = false) MissionCancelReqVO request) {
        MissionCancelCommand command = new MissionCancelCommand(); if (request != null) command.setReason(request.getReason());
        missions.cancel(id, command); return success(true);
    }

    private static MissionRespVO response(MissionRespDTO value) {
        MissionRespVO result = new MissionRespVO(); result.setId(value.id()); result.setMissionNo(value.missionNo()); result.setRobotId(value.robotId());
        result.setMissionType(value.missionType()); result.setSource(value.source()); result.setStatus(value.status().name()); result.setPriority(value.priority());
        result.setRequestId(value.requestId()); result.setScheduledTime(value.scheduledTime()); result.setStartedTime(value.startedTime());
        result.setFinishedTime(value.finishedTime()); result.setErrorCode(value.errorCode()); result.setErrorMessage(value.errorMessage()); return result;
    }

    private static MissionRespVO detailResponse(MissionDetailDTO value) {
        MissionRespVO result = response(value.mission());
        result.setActions(value.actions().stream().map(action -> {
            MissionRespVO.MissionActionVO item = new MissionRespVO.MissionActionVO();
            item.setId(action.id()); item.setSequenceNo(action.sequenceNo()); item.setActionType(action.actionType());
            item.setParameters(action.parameters()); item.setStatus(action.status()); item.setStartedTime(action.startedTime());
            item.setFinishedTime(action.finishedTime()); item.setErrorCode(action.errorCode()); item.setErrorMessage(action.errorMessage());
            return item;
        }).toList());
        result.setEvents(value.events().stream().map(event -> {
            MissionRespVO.MissionEventVO item = new MissionRespVO.MissionEventVO();
            item.setId(event.id()); item.setActionId(event.actionId()); item.setEventType(event.eventType());
            item.setFromStatus(event.fromStatus()); item.setToStatus(event.toStatus()); item.setPayload(event.payload());
            item.setOccurredTime(event.occurredTime());
            return item;
        }).toList());
        return result;
    }
}
