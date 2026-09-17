package com.robot.platform.robot.mission.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.command.gateway.RobotCommand;
import com.robot.platform.robot.command.gateway.RobotCommandGateway;
import com.robot.platform.robot.mission.dal.dataobject.MissionActionDO;
import com.robot.platform.robot.mission.dal.dataobject.MissionDO;
import com.robot.platform.robot.mission.dal.dataobject.MissionEventDO;
import com.robot.platform.robot.mission.dal.mysql.MissionActionMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionEventMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionExecutionMapper;
import com.robot.platform.robot.mission.domain.MissionAggregate;
import com.robot.platform.robot.mission.domain.MissionActionState;
import com.robot.platform.robot.mission.domain.MissionStateMachine;
import com.robot.platform.robot.mission.domain.MissionTransitionContext;
import com.robot.platform.robot.mission.domain.MissionTransitionResult;
import com.robot.platform.robot.mission.enums.MissionActionType;
import com.robot.platform.robot.mission.enums.MissionSource;
import com.robot.platform.robot.mission.enums.MissionStatus;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCancelCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.command.MissionPageQuery;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import com.robot.platform.robot.mission.service.dto.MissionDetailDTO;
import com.robot.platform.robot.mission.message.MissionAckPayload;
import com.robot.platform.robot.mission.message.MissionEventPayload;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.robot.mission.enums.MissionErrorCodeConstants.*;

/** Transactional Mission application service. No controller or message adapter reaches Mission mappers directly. */
@Service
@Slf4j
public class MissionServiceImpl implements MissionService {
    private final MissionMapper missions;
    private final MissionActionMapper actions;
    private final MissionEventMapper events;
    private final MissionExecutionMapper executions;
    private final RobotMapper robots;
    private final RobotLiveStatusQueryService liveStatuses;
    private final MissionStateMachine stateMachine;
    private final RobotCommandGateway commands;
    private final RobotRealtimeEventPublisher realtime;
    private final Clock clock;

    /** Compact constructor keeps pure service tests independent from a future transport implementation. */
    public MissionServiceImpl(MissionMapper missions, MissionActionMapper actions, MissionEventMapper events, MissionExecutionMapper executions,
                              RobotMapper robots, RobotLiveStatusQueryService liveStatuses,
                              MissionStateMachine stateMachine) {
        this(missions, actions, events, executions, robots, liveStatuses, stateMachine, (RobotCommandGateway) null, event -> { }, Clock.systemUTC());
    }

    @Autowired
    public MissionServiceImpl(MissionMapper missions, MissionActionMapper actions, MissionEventMapper events, MissionExecutionMapper executions,
                              RobotMapper robots, RobotLiveStatusQueryService liveStatuses,
                              MissionStateMachine stateMachine, org.springframework.beans.factory.ObjectProvider<RobotCommandGateway> commands,
                              org.springframework.beans.factory.ObjectProvider<RobotRealtimeEventPublisher> realtime, Clock robotHeartbeatClock) {
        this(missions, actions, events, executions, robots, liveStatuses, stateMachine, commands.getIfAvailable(),
                realtime.getIfAvailable(() -> event -> { }), robotHeartbeatClock);
    }

    /** Explicit transport constructor is for focused tests and a future alternate gateway implementation. */
    public MissionServiceImpl(MissionMapper missions, MissionActionMapper actions, MissionEventMapper events, MissionExecutionMapper executions,
                              RobotMapper robots, RobotLiveStatusQueryService liveStatuses, MissionStateMachine stateMachine,
                              RobotCommandGateway commands, Clock clock) {
        this(missions, actions, events, executions, robots, liveStatuses, stateMachine, commands, event -> { }, clock);
    }
    public MissionServiceImpl(MissionMapper missions, MissionActionMapper actions, MissionEventMapper events, MissionExecutionMapper executions,
                              RobotMapper robots, RobotLiveStatusQueryService liveStatuses, MissionStateMachine stateMachine,
                              RobotCommandGateway commands, RobotRealtimeEventPublisher realtime, Clock clock) {
        this.missions = missions; this.actions = actions; this.events = events; this.executions = executions; this.robots = robots;
        this.liveStatuses = liveStatuses; this.stateMachine = stateMachine; this.commands = commands; this.realtime = realtime; this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MissionRespDTO create(MissionCreateCommand command) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        validateCreate(command);
        String payload = canonicalPayload(command.getActions());
        MissionDO existing = missions.selectByRequestId(command.getRequestId());
        if (existing != null) return compatibleResponse(existing, command, payload);
        // Tenant-intercepted select is the server-side authority check; request payload never supplies tenantId.
        RobotDO robot = robots.selectById(command.getRobotId());
        if (robot == null || !Objects.equals(robot.getTenantId(), tenantId)) throw exception(MISSION_ROBOT_NOT_AVAILABLE);
        MissionDO mission = new MissionDO();
        mission.setTenantId(tenantId); mission.setMissionNo("M" + UUID.randomUUID().toString().replace("-", ""));
        mission.setRobotId(robot.getId()); mission.setMissionType(command.getMissionType()); mission.setSource(command.getSource());
        mission.setStatus(MissionStatus.PENDING.name()); mission.setPriority(command.getPriority()); mission.setRequestId(command.getRequestId());
        mission.setCreatorId(command.getCreatorId()); mission.setScheduledTime(command.getScheduledTime()); mission.setPayload(payload); mission.setVersion(0);
        try {
            missions.insert(mission);
        } catch (DuplicateKeyException duplicate) {
            MissionDO winner = missions.selectCurrentByRequestId(tenantId, command.getRequestId());
            if (winner == null) throw duplicate; // Do not hide another unique constraint failure.
            return compatibleResponse(winner, command, payload);
        }
        for (int i = 0; i < command.getActions().size(); i++) {
            MissionActionCommand action = command.getActions().get(i);
            MissionActionDO row = new MissionActionDO();
            row.setTenantId(tenantId); row.setMissionId(mission.getId()); row.setSequenceNo(i + 1);
            row.setActionType(action.actionType()); row.setParameters(action.parameters()); row.setStatus("PENDING"); actions.insert(row);
        }
        appendEvent(mission, null, "MISSION_CREATED", MissionStatus.CREATED, MissionStatus.PENDING, null, now());
        // Offline is not failure: the pending row forms the durable queue consumed by Task 11's dispatcher.
        if (liveStatuses.getStatus(robot.getId()) == null) log.debug("[create][No live projection; mission remains pending missionId({})]", mission.getId());
        return response(mission);
    }

    @Override
    public MissionRespDTO get(long id) { return response(requireMission(id)); }

    @Override
    public MissionDetailDTO detail(long id) {
        MissionDO mission = requireMission(id);
        List<MissionDetailDTO.Action> actionTimeline = actions.selectByMission(mission.getTenantId(), mission.getId()).stream()
                .map(action -> new MissionDetailDTO.Action(action.getId(), action.getSequenceNo(), action.getActionType(),
                        action.getParameters(), action.getStatus(), action.getStartedTime(), action.getFinishedTime(),
                        action.getErrorCode(), action.getErrorMessage()))
                .toList();
        List<MissionDetailDTO.Event> eventTimeline = events.selectByMission(mission.getTenantId(), mission.getId()).stream()
                .map(event -> new MissionDetailDTO.Event(event.getId(), event.getActionId(), event.getEventType(),
                        event.getFromStatus(), event.getToStatus(), event.getPayload(), event.getOccurredTime()))
                .toList();
        return new MissionDetailDTO(response(mission), actionTimeline, eventTimeline);
    }

    @Override
    public PageResult<MissionRespDTO> page(MissionPageQuery query) {
        PageResult<MissionDO> page = missions.selectPage(query);
        return new PageResult<>(page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(long id, MissionCancelCommand command) {
        MissionDO mission = requireMission(id);
        MissionStatus current = status(mission);
        if (current.isTerminal()) return; // idempotent cancel cannot rewrite the final result.
        if (current == MissionStatus.PENDING) {
            transitionAndEvent(mission, MissionStatus.CANCELLED, null, command == null ? null : command.getReason());
            return;
        }
        if (current == MissionStatus.DISPATCHED || current == MissionStatus.RUNNING || current == MissionStatus.PAUSED) {
            if (mission.getCancelRequestedTime() != null) return;
            if (missions.requestCancelIfVersion(id, mission.getTenantId(), mission.getVersion(), now()) == 0) {
                MissionDO latest = missions.selectCurrentById(mission.getTenantId(), id);
                if (latest != null && (latest.getCancelRequestedTime() != null || status(latest).isTerminal())) return;
                throw exception(MISSION_CONCURRENT_MODIFICATION);
            }
            // The cancellation fact is committed with this fence, so a due START cannot survive
            // as RETRY/PENDING and be delivered after the user has cancelled the mission.
            if (commands == null) throw new IllegalStateException("RobotCommandGateway is unavailable");
            RobotCommandGateway.StartCancellation cancellation = commands.invalidateUndeliveredStarts(mission.getTenantId(), mission.getId());
            // A command in PUBLISHING cannot be safely retracted. The cancel command below is a
            // durable compensation: MQTT may reorder physical calls, but its START tombstone
            // obliges the robot to reject that Start rather than execute it.
            String eventType = cancellation != null && cancellation.outcome() == RobotCommandGateway.StartCancellationOutcome.COMPENSATING
                    ? "CANCEL_REQUESTED_AFTER_START_PUBLISHING" : "CANCEL_REQUESTED";
            appendEvent(mission, null, eventType, current, current, null, now());
            Map<String, Object> cancelPayload = new LinkedHashMap<>();
            cancelPayload.put("reason", command == null ? "" : Objects.toString(command.getReason(), ""));
            if (cancellation != null && !blank(cancellation.startMessageId())) {
                // Robot protocol tombstone: reject this START even when MQTT delivers cancel first.
                cancelPayload.put("targetStartMessageId", cancellation.startMessageId());
            }
            commands.enqueue(new RobotCommand(mission.getTenantId(), mission.getRobotId(), mission.getId(), mission.getRequestId(),
                    "MISSION_CANCEL", JsonUtils.toJsonString(cancelPayload)));
            return;
        }
        throw exception(MISSION_ILLEGAL_TRANSITION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutPending(long id) {
        MissionDO mission = requireMission(id);
        if (status(mission) != MissionStatus.PENDING) return;
        transitionAndEvent(mission, MissionStatus.FAILED, "MISSION_WAIT_TIMEOUT", "mission waited too long for robot");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchPending(long id) {
        MissionDO mission = requireMission(id);
        if (status(mission) != MissionStatus.PENDING) return;
        if (commands == null) throw new IllegalStateException("RobotCommandGateway is unavailable");
        // The state transition and gateway's durable outbox insert share this transaction. If either
        // fails, the mission remains PENDING and another scheduler pass can safely retry it.
        transitionAndEvent(mission, MissionStatus.DISPATCHED, null, null);
        commands.enqueue(new RobotCommand(mission.getTenantId(), mission.getRobotId(), mission.getId(), mission.getRequestId(),
                "MISSION_START", commandPayload(mission)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commandDeliveryExhausted(long missionId, String commandType, String errorCode, String errorMessage) {
        MissionDO mission = requireMission(missionId);
        if (status(mission).isTerminal()) return;
        String code = requiredCode(errorCode, "MISSION_COMMAND_DELIVERY_EXHAUSTED");
        String message = requiredMessage(errorMessage, "robot command delivery exhausted");
        appendEvent(mission, null, "COMMAND_DELIVERY_EXHAUSTED_" + Objects.toString(commandType, "UNKNOWN"),
                status(mission), status(mission), null, now());
        transitionAndEvent(mission, MissionStatus.FAILED, code, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageHandleResult acknowledge(long deviceId, String requestId, String messageId, MissionAckPayload payload) {
        if (payload == null || payload.missionId() <= 0 || blank(payload.commandMessageId())) throw new IllegalArgumentException("invalid mission acknowledgement");
        MissionDO mission = lockedOwnedMission(deviceId, payload.missionId(), requestId);
        if (status(mission).isTerminal()) return MessageHandleResult.LATE_IGNORED;
        RobotCommandGateway.RobotCommandReceipt command = commandForAck(payload.commandMessageId(), mission, deviceId);
        executions.upsertAcknowledgement(mission.getTenantId(), mission.getId(), Math.max(command.deliveryAttempt(), 1),
                payload.commandMessageId(), command.messageType(), now(), payload.accepted() ? "ACCEPTED" : "REJECTED");
        boolean cancel = "MISSION_CANCEL".equals(command.messageType());
        appendEvent(mission, null, payload.accepted() ? (cancel ? "MISSION_CANCEL_ACK" : "MISSION_ACK")
                : (cancel ? "MISSION_CANCEL_REJECTED" : "MISSION_REJECTED"), status(mission), status(mission), messageId, now());
        if (payload.accepted() && cancel) {
            transitionAndEvent(mission, MissionStatus.CANCELLED, null, null);
        } else if (!payload.accepted() && !cancel) {
            transitionAndEvent(mission, MissionStatus.FAILED, "MISSION_REJECTED", requiredMessage(payload.reason(), "robot rejected mission"));
        }
        return MessageHandleResult.ACCEPTED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageHandleResult handleAction(long deviceId, String requestId, String messageId, MissionEventPayload payload) {
        if (payload == null || !"ACTION".equals(payload.kind()) || payload.missionId() <= 0 || payload.actionId() == null
                || blank(payload.status())) throw new IllegalArgumentException("invalid mission action event");
        MissionDO mission = lockedOwnedMission(deviceId, payload.missionId(), requestId);
        if (status(mission).isTerminal()) return MessageHandleResult.LATE_IGNORED;
        String actionStatus = payload.status();
        if (!List.of("RUNNING", "SUCCESS", "FAILED", "SKIPPED").contains(actionStatus)) throw new IllegalArgumentException("invalid action status");
        MissionActionDO action = actions.selectOwned(mission.getTenantId(), mission.getId(), payload.actionId());
        if (action == null) throw new IllegalArgumentException("mission action is not owned by mission");
        if (actionStatus.equals(action.getStatus())) return MessageHandleResult.LATE_IGNORED;
        if (!isActionTransitionAllowed(action.getStatus(), actionStatus)) throw new IllegalArgumentException("illegal mission action transition");
        boolean terminal = "SUCCESS".equals(actionStatus) || "FAILED".equals(actionStatus) || "SKIPPED".equals(actionStatus);
        if (actions.updateProgress(mission.getTenantId(), mission.getId(), payload.actionId(), action.getStatus(), actionStatus, terminal, now(),
                payload.errorCode(), payload.errorMessage()) != 1) throw new IllegalArgumentException("mission action is not owned by mission");
        appendEvent(mission, payload.actionId(), "ACTION_" + actionStatus, status(mission), status(mission), messageId, now());
        if (("RUNNING".equals(actionStatus) || "SUCCESS".equals(actionStatus)) && status(mission) == MissionStatus.DISPATCHED) {
            transitionAndEvent(mission, MissionStatus.RUNNING, null, null);
            mission = missions.selectCurrentById(mission.getTenantId(), mission.getId());
        }
        if ("FAILED".equals(actionStatus)) transitionAndEvent(mission, MissionStatus.FAILED,
                requiredCode(payload.errorCode(), "ACTION_FAILED"), requiredMessage(payload.errorMessage(), "robot action failed"));
        if ("SUCCESS".equals(actionStatus) && status(mission) == MissionStatus.RUNNING && allRequiredActionsSucceeded(mission)) {
            transitionAndEvent(mission, MissionStatus.SUCCESS, null, null);
        }
        return MessageHandleResult.ACCEPTED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageHandleResult handleResult(long deviceId, String requestId, String messageId, MissionEventPayload payload) {
        if (payload == null || !"RESULT".equals(payload.kind()) || payload.missionId() <= 0 || blank(payload.status())) {
            throw new IllegalArgumentException("invalid mission result event");
        }
        MissionDO mission = lockedOwnedMission(deviceId, payload.missionId(), requestId);
        if (status(mission).isTerminal()) return MessageHandleResult.LATE_IGNORED;
        MissionStatus target;
        try { target = MissionStatus.valueOf(payload.status()); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("invalid mission result status", invalid); }
        if (!target.isTerminal()) throw new IllegalArgumentException("mission result must be terminal");
        appendEvent(mission, null, "MISSION_" + target.name(), status(mission), target, messageId, now());
        if (target == MissionStatus.SUCCESS && status(mission) == MissionStatus.DISPATCHED) {
            transitionAndEvent(mission, MissionStatus.RUNNING, null, null);
            mission = missions.selectCurrentById(mission.getTenantId(), mission.getId());
        }
        transitionAndEvent(mission, target, target == MissionStatus.FAILED ? requiredCode(payload.errorCode(), "MISSION_FAILED") : null,
                target == MissionStatus.FAILED ? requiredMessage(payload.errorMessage(), "robot reported mission failure") : null);
        return MessageHandleResult.ACCEPTED;
    }

    private void transitionAndEvent(MissionDO mission, MissionStatus target, String errorCode, String errorMessage) {
        MissionTransitionResult transition;
        try {
            transition = stateMachine.transition(aggregate(mission), target,
                    new MissionTransitionContext(clock.instant(), errorCode, errorMessage));
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            throw exception(MISSION_ILLEGAL_TRANSITION);
        }
        MissionAggregate after = transition.after();
        if (missions.transitionIfVersion(mission.getId(), mission.getTenantId(), transition.from().name(), transition.to().name(),
                mission.getVersion(), time(after.startedAt()), time(after.finishedAt()), after.errorCode(), after.errorMessage()) == 0) {
            throw exception(MISSION_CONCURRENT_MODIFICATION);
        }
        appendEvent(mission, null, "STATUS_CHANGED", transition.from(), transition.to(), null, now());
        publishMissionStatus(after, mission);
    }

    private MissionDO requireMission(long id) {
        MissionDO mission = missions.selectById(id);
        if (mission == null) throw exception(MISSION_NOT_EXISTS);
        return mission;
    }

    /** Device/tenant binding is re-derived in the service, never trusted from MQTT/HTTP payloads. */
    private MissionDO lockedOwnedMission(long deviceId, long missionId, String requestId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        RobotDO robot = robots.selectByTenantAndDeviceId(tenantId, deviceId);
        MissionDO mission = missions.selectCurrentById(tenantId, missionId);
        if (robot == null || mission == null || !Objects.equals(mission.getRobotId(), robot.getId())
                || !Objects.equals(mission.getRequestId(), requestId)) throw exception(MISSION_NOT_EXISTS);
        return mission;
    }

    private RobotCommandGateway.RobotCommandReceipt commandForAck(String commandMessageId, MissionDO mission, long deviceId) {
        if (commands == null) throw new IllegalStateException("RobotCommandGateway is unavailable");
        RobotCommandGateway.RobotCommandReceipt command = commands.findCommand(commandMessageId)
                .orElseThrow(() -> new IllegalArgumentException("unknown mission command acknowledgement"));
        if (command.tenantId() != mission.getTenantId() || command.deviceId() != deviceId || command.robotId() != mission.getRobotId()
                || command.missionId() != mission.getId() || !Objects.equals(command.requestId(), mission.getRequestId())
                || !("MISSION_START".equals(command.messageType()) || "MISSION_CANCEL".equals(command.messageType()))) {
            throw new IllegalArgumentException("mission acknowledgement does not belong to the device command");
        }
        return command;
    }

    private boolean allRequiredActionsSucceeded(MissionDO mission) {
        // Every configured action is required today.  The count is evaluated after the action CAS
        // update inside the same transaction, so a stale list cannot prematurely finish a mission.
        return actions.countNotSucceeded(mission.getTenantId(), mission.getId()) == 0;
    }

    private static boolean isActionTransitionAllowed(String source, String target) {
        return ("PENDING".equals(source) && List.of("RUNNING", "SUCCESS", "FAILED", "SKIPPED").contains(target))
                || ("RUNNING".equals(source) && List.of("SUCCESS", "FAILED", "SKIPPED").contains(target));
    }

    private MissionRespDTO compatibleResponse(MissionDO existing, MissionCreateCommand command, String payload) {
        // MySQL JSON columns normalize whitespace/key order, so compare data rather than serialized text.
        if (Objects.equals(existing.getRobotId(), command.getRobotId()) && Objects.equals(existing.getSource(), command.getSource())
                && Objects.equals(existing.getMissionType(), command.getMissionType())
                && Objects.equals(JsonUtils.parseObject(existing.getPayload(), com.fasterxml.jackson.databind.JsonNode.class),
                JsonUtils.parseObject(payload, com.fasterxml.jackson.databind.JsonNode.class))) return response(existing);
        throw exception(MISSION_REQUEST_ID_CONFLICT);
    }

    private void validateCreate(MissionCreateCommand command) {
        if (command == null || command.getRobotId() == null || blank(command.getMissionType()) || blank(command.getRequestId())
                || blank(command.getSource()) || command.getPriority() == null || command.getPriority() < 0 || command.getPriority() > 100
                || command.getActions() == null || command.getActions().isEmpty()) throw exception(MISSION_INVALID_REQUEST);
        try { MissionSource.valueOf(command.getSource()); } catch (IllegalArgumentException invalid) { throw exception(MISSION_INVALID_REQUEST); }
        for (MissionActionCommand action : command.getActions()) validateAction(action);
    }

    @SuppressWarnings("unchecked")
    private void validateAction(MissionActionCommand action) {
        if (action == null || blank(action.actionType()) || blank(action.parameters())) throw exception(MISSION_INVALID_REQUEST);
        MissionActionType type;
        try { type = MissionActionType.valueOf(action.actionType()); } catch (IllegalArgumentException invalid) { throw exception(MISSION_INVALID_REQUEST); }
        Map<String, Object> parameters;
        try { parameters = JsonUtils.parseObject(action.parameters(), Map.class); } catch (RuntimeException invalid) { throw exception(MISSION_INVALID_REQUEST); }
        if (parameters == null || (type == MissionActionType.NAVIGATE && !parameters.containsKey("target"))
                || (type == MissionActionType.SPEAK && !parameters.containsKey("text"))
                || (type == MissionActionType.PLAY_MEDIA && !parameters.containsKey("mediaUrl"))
                || (type == MissionActionType.WAIT && !parameters.containsKey("seconds"))) throw exception(MISSION_INVALID_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private String canonicalPayload(List<MissionActionCommand> commandActions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (MissionActionCommand action : commandActions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", action.actionType()); item.put("params", JsonUtils.parseObject(action.parameters(), Map.class)); normalized.add(item);
        }
        return JsonUtils.toJsonString(Map.of("actions", normalized));
    }

    /**
     * Preserve the user-defined action body but add the durable action identity used by robot
     * progress callbacks.  Sending anonymous actions would make a valid ACTION event impossible
     * to authorize against {@code robot_mission_action}, so this is intentionally a dispatch
     * boundary concern rather than a client-controlled payload field.
     */
    @SuppressWarnings("unchecked")
    private String commandPayload(MissionDO mission) {
        List<Map<String, Object>> wireActions = new ArrayList<>();
        for (MissionActionDO action : actions.selectByMission(mission.getTenantId(), mission.getId())) {
            if (action.getId() == null || blank(action.getActionType()) || blank(action.getParameters())) {
                throw new IllegalStateException("persisted mission action is not dispatchable");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", action.getId());
            item.put("type", action.getActionType());
            item.put("params", JsonUtils.parseObject(action.getParameters(), Map.class));
            wireActions.add(item);
        }
        if (wireActions.isEmpty()) throw new IllegalStateException("mission has no dispatchable actions");
        return JsonUtils.toJsonString(Map.of("actions", wireActions));
    }

    private void appendEvent(MissionDO mission, Long actionId, String type, MissionStatus from, MissionStatus to,
                             String messageId, LocalDateTime occurredAt) {
        MissionEventDO event = new MissionEventDO();
        event.setTenantId(mission.getTenantId()); event.setMissionId(mission.getId()); event.setActionId(actionId);
        event.setEventType(type); event.setFromStatus(from.name()); event.setToStatus(to.name()); event.setMessageId(messageId);
        event.setRequestId(mission.getRequestId()); event.setOccurredTime(occurredAt); event.setPayload("{}"); events.insert(event);
    }

    private MissionAggregate aggregate(MissionDO mission) {
        return new MissionAggregate(mission.getId(), mission.getTenantId(), mission.getRobotId(), status(mission), mission.getPriority(),
                instant(mission.getStartedTime()), instant(mission.getFinishedTime()), mission.getVersion(),
                actions.selectByMission(mission.getTenantId(), mission.getId()).stream()
                        .map(action -> new MissionActionState(action.getId(), action.getStatus(), true)).toList(),
                mission.getErrorCode(), mission.getErrorMessage());
    }
    /** Mission lifecycle notifications use the existing tenant realtime outbox and never bypass its retry semantics. */
    private void publishMissionStatus(MissionAggregate after, MissionDO mission) {
        RobotLiveStatus live = liveStatuses.getStatus(mission.getRobotId());
        if (live == null) live = new RobotLiveStatus(RobotOnlineStatus.OFFLINE, RobotWorkStatus.IDLE, null);
        realtime.publish(new TenantRobotRealtimeEvent(1, mission.getTenantId(), mission.getRobotId(), mission.getRequestId(),
                "MISSION_STATUS_CHANGED", clock.instant(), live, "MISSION-" + mission.getId() + "-" + after.version()));
    }
    private MissionStatus status(MissionDO mission) { return MissionStatus.valueOf(mission.getStatus()); }
    private MissionRespDTO response(MissionDO mission) { return new MissionRespDTO(mission.getId(), mission.getMissionNo(), mission.getRobotId(), mission.getMissionType(), mission.getSource(), status(mission), mission.getPriority(), mission.getRequestId(), mission.getScheduledTime(), mission.getStartedTime(), mission.getFinishedTime(), mission.getErrorCode(), mission.getErrorMessage()); }
    private LocalDateTime now() { return time(clock.instant()); }
    private static LocalDateTime time(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String requiredCode(String value, String fallback) { return blank(value) ? fallback : value; }
    private static String requiredMessage(String value, String fallback) { return blank(value) ? fallback : value; }
}
