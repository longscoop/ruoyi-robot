package com.robot.platform.robot.mission;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.robot.mission.dal.dataobject.MissionDO;
import com.robot.platform.robot.mission.dal.mysql.MissionActionMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionEventMapper;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.domain.MissionStateMachine;
import com.robot.platform.robot.mission.enums.MissionStatus;
import com.robot.platform.robot.mission.service.MissionServiceImpl;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import com.robot.platform.robot.command.gateway.RobotCommandGateway;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MissionServiceTest {
    private final MissionMapper missions = mock(MissionMapper.class);
    private final MissionActionMapper actions = mock(MissionActionMapper.class);
    private final MissionEventMapper events = mock(MissionEventMapper.class);
    private final com.robot.platform.robot.mission.dal.mysql.MissionExecutionMapper executions = mock(com.robot.platform.robot.mission.dal.mysql.MissionExecutionMapper.class);
    private final RobotMapper robots = mock(RobotMapper.class);
    private final RobotLiveStatusQueryService liveStatuses = mock(RobotLiveStatusQueryService.class);
    private final MissionServiceImpl service = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
            new MissionStateMachine());

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void requestIdRetryReturnsOnlyCompatibleExistingMission() {
        TenantContextHolder.setTenantId(10L);
        MissionDO existing = mission(7L, 10L, 20L, "request-1", "ADMIN", "{\"actions\":[{\"type\":\"WAIT\",\"params\":{\"seconds\":5}}]}");
        when(missions.selectByRequestId("request-1")).thenReturn(existing);

        MissionRespDTO result = service.create(command(20L, "request-1", "ADMIN"));

        assertThat(result.id()).isEqualTo(7L);
        verifyNoInteractions(actions, events, robots, liveStatuses);
    }

    @Test
    void conflictingRequestIdPayloadIsRejectedWithoutMutatingMission() {
        TenantContextHolder.setTenantId(10L);
        when(missions.selectByRequestId("request-1")).thenReturn(
                mission(7L, 10L, 20L, "request-1", "ADMIN", "{\"actions\":[{\"type\":\"SPEAK\"}]}"));

        assertThatThrownBy(() -> service.create(command(20L, "request-1", "ADMIN"))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(actions, events, robots, liveStatuses);
    }

    @Test
    void requestIdRetryAcceptsEquivalentMysqlJsonFormatting() {
        TenantContextHolder.setTenantId(10L);
        when(missions.selectByRequestId("request-json")).thenReturn(mission(7L, 10L, 20L, "request-json", "ADMIN",
                "{\"actions\": [{\"params\": {\"seconds\": 5}, \"type\": \"WAIT\"}]}"));
        assertThat(service.create(command(20L, "request-json", "ADMIN")).id()).isEqualTo(7L);
        verifyNoInteractions(actions, events, robots, liveStatuses);
    }

    @Test
    void rejectsRobotOutsideCallerTenantBeforeCreatingAnything() {
        TenantContextHolder.setTenantId(10L);
        when(robots.selectById(20L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(command(20L, "request-2", "ADMIN"))).isInstanceOf(RuntimeException.class);
        verify(missions, never()).insert(org.mockito.ArgumentMatchers.<MissionDO>any());
    }

    @Test
    void offlineRobotMissionIsPersistedPendingWithActionsAndInitialEventAtomically() {
        TenantContextHolder.setTenantId(10L);
        when(robots.selectById(20L)).thenReturn(robot(20L, 10L));
        when(liveStatuses.getStatus(20L)).thenReturn(new RobotLiveStatus(
                com.robot.platform.robot.robot.enums.RobotOnlineStatus.OFFLINE,
                com.robot.platform.robot.robot.enums.RobotWorkStatus.IDLE, 80));
        AtomicLong ids = new AtomicLong(100);
        doAnswer(invocation -> { invocation.<MissionDO>getArgument(0).setId(ids.incrementAndGet()); return 1; }).when(missions).insert(org.mockito.ArgumentMatchers.<MissionDO>any());

        MissionRespDTO result = service.create(command(20L, "request-3", "ADMIN"));

        assertThat(result.status()).isEqualTo(MissionStatus.PENDING);
        verify(actions).insert(org.mockito.ArgumentMatchers.<com.robot.platform.robot.mission.dal.dataobject.MissionActionDO>any());
        verify(events).insert(org.mockito.ArgumentMatchers.<com.robot.platform.robot.mission.dal.dataobject.MissionEventDO>argThat(event -> event.getFromStatus().equals("CREATED")
                && event.getToStatus().equals("PENDING")));
    }

    @Test
    void invalidActionSchemaIsRejectedBeforePersisting() {
        TenantContextHolder.setTenantId(10L);
        MissionCreateCommand invalid = command(20L, "request-4", "ADMIN");
        invalid.setActions(List.of(new MissionActionCommand("NAVIGATE", "{}")));

        assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(missions, actions, events, robots, liveStatuses);
    }

    @Test
    void cancellingPendingMissionTransitionsDirectlyToCancelled() {
        TenantContextHolder.setTenantId(10L);
        MissionDO pending = mission(7L, 10L, 20L, "request-cancel", "ADMIN", "{}");
        when(missions.selectById(7L)).thenReturn(pending);
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(1);

        service.cancel(7L, new com.robot.platform.robot.mission.service.command.MissionCancelCommand());

        verify(missions).transitionIfVersion(eq(7L), eq(10L), eq("PENDING"), eq("CANCELLED"), eq(0),
                isNull(), any(), isNull(), isNull());
    }

    @Test
    void cancellingDispatchedMissionRecordsRequestAndUsesOnlyGateway() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC));
        MissionDO dispatched = mission(8L, 10L, 20L, "request-dispatched", "ADMIN", "{}"); dispatched.setStatus("DISPATCHED");
        when(missions.selectById(8L)).thenReturn(dispatched); when(missions.requestCancelIfVersion(8L, 10L, 0, java.time.LocalDateTime.of(2026, 9, 14, 0, 0))).thenReturn(1);

        withGateway.cancel(8L, new com.robot.platform.robot.mission.service.command.MissionCancelCommand());

        verify(gateway).enqueue(org.mockito.ArgumentMatchers.argThat(command -> command.missionId() == 8L && command.type().equals("MISSION_CANCEL")));
        verify(gateway).invalidateUndeliveredStarts(10L, 8L);
        verify(missions, never()).transitionIfVersion(eq(8L), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    /** A cancel-first MQTT delivery must tombstone the exact durable START envelope at the robot. */
    @Test
    void cancellationAfterStartPublishPhaseCarriesStartMessageTombstone() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC));
        MissionDO dispatched = mission(18L, 10L, 20L, "request-tombstone", "ADMIN", "{}"); dispatched.setStatus("DISPATCHED");
        when(missions.selectById(18L)).thenReturn(dispatched);
        when(missions.requestCancelIfVersion(18L, 10L, 0, LocalDateTime.of(2026, 9, 14, 0, 0))).thenReturn(1);
        when(gateway.invalidateUndeliveredStarts(10L, 18L)).thenReturn(new RobotCommandGateway.StartCancellation(
                RobotCommandGateway.StartCancellationOutcome.COMPENSATING, "01K5K8VJGR9VK8T3H7ZQX3W1AA"));

        withGateway.cancel(18L, new com.robot.platform.robot.mission.service.command.MissionCancelCommand());

        verify(gateway).enqueue(org.mockito.ArgumentMatchers.argThat(command -> command.type().equals("MISSION_CANCEL")
                && com.robot.platform.robot.mission.MissionServiceTest.jsonValue(command.payload(), "targetStartMessageId")
                .equals("01K5K8VJGR9VK8T3H7ZQX3W1AA")));
        verify(events).insert(org.mockito.ArgumentMatchers.<com.robot.platform.robot.mission.dal.dataobject.MissionEventDO>argThat(event ->
                event.getEventType().equals("CANCEL_REQUESTED_AFTER_START_PUBLISHING")));
    }

    /** The device receives durable action ids, otherwise it cannot report ACTION progress safely. */
    @Test
    void dispatchCommandCarriesPersistedActionIdsInsteadOfOnlyAnonymousPayloadActions() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
        MissionDO pending = mission(21L, 10L, 20L, "request-wire-actions", "ADMIN",
                "{\"actions\":[{\"type\":\"WAIT\",\"params\":{\"seconds\":5}}]}");
        com.robot.platform.robot.mission.dal.dataobject.MissionActionDO saved = action(501L, 21L, "PENDING");
        saved.setActionType("WAIT"); saved.setParameters("{\"seconds\":5}"); saved.setSequenceNo(1);
        when(missions.selectById(21L)).thenReturn(pending);
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(1);
        when(actions.selectByMission(10L, 21L)).thenReturn(List.of(saved));

        withGateway.dispatchPending(21L);

        verify(gateway).enqueue(org.mockito.ArgumentMatchers.argThat(command -> command.type().equals("MISSION_START")
                && jsonActionId(command.payload()) == 501L));
    }

    private static String jsonValue(String payload, String key) {
        return com.robot.platform.framework.common.util.json.JsonUtils.parseObject(payload, java.util.Map.class).get(key).toString();
    }

    @SuppressWarnings("unchecked")
    private static long jsonActionId(String payload) {
        java.util.Map<String, Object> value = com.robot.platform.framework.common.util.json.JsonUtils.parseObject(payload, java.util.Map.class);
        return ((Number) ((java.util.Map<String, Object>) ((java.util.List<?>) value.get("actions")).get(0)).get("id")).longValue();
    }

    @Test
    void timeoutDoesNotWinWhenConcurrentCancelAlreadyChangedVersion() {
        TenantContextHolder.setTenantId(10L);
        MissionDO pending = mission(9L, 10L, 20L, "request-timeout", "ADMIN", "{}");
        when(missions.selectById(9L)).thenReturn(pending);
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.timeoutPending(9L)).isInstanceOf(RuntimeException.class);
        verify(events, never()).insert(org.mockito.ArgumentMatchers.<com.robot.platform.robot.mission.dal.dataobject.MissionEventDO>any());
    }

    /** A cancellation ACK must be bound to the durable cancel command, not just a robot supplied id. */
    @Test
    void acceptedCancelAcknowledgementCompletesOnlyTheMatchingMission() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
        MissionDO dispatched = mission(12L, 10L, 20L, "request-ack", "ADMIN", "{}"); dispatched.setStatus("DISPATCHED");
        when(robots.selectByTenantAndDeviceId(10L, 4L)).thenReturn(robot(20L, 10L));
        when(missions.selectCurrentById(10L, 12L)).thenReturn(dispatched);
        when(gateway.findCommand("01K5K8VJGR9VK8T3H7ZQX3W1AC")).thenReturn(Optional.of(
                new RobotCommandGateway.RobotCommandReceipt(10L, 4L, 20L, 12L, "request-ack", "MISSION_CANCEL", 1)));
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(1);

        assertThat(withGateway.acknowledge(4L, "request-ack", "01K5K8VJGR9VK8T3H7ZQX3W1AD",
                new com.robot.platform.robot.mission.message.MissionAckPayload(12L, "01K5K8VJGR9VK8T3H7ZQX3W1AC", true, null)))
                .isEqualTo(com.robot.platform.robot.mission.message.MessageHandleResult.ACCEPTED);

        verify(missions).transitionIfVersion(eq(12L), eq(10L), eq("DISPATCHED"), eq("CANCELLED"), eq(0),
                isNull(), any(), isNull(), isNull());
        verify(executions).upsertAcknowledgement(eq(10L), eq(12L), eq(1), eq("01K5K8VJGR9VK8T3H7ZQX3W1AC"),
                eq("MISSION_CANCEL"), any(), eq("ACCEPTED"));
    }

    @Test
    void acknowledgementForAnotherRobotCommandIsRejectedBeforeAnyTransition() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.systemUTC());
        MissionDO dispatched = mission(13L, 10L, 20L, "request-bound", "ADMIN", "{}"); dispatched.setStatus("DISPATCHED");
        when(robots.selectByTenantAndDeviceId(10L, 4L)).thenReturn(robot(20L, 10L));
        when(missions.selectCurrentById(10L, 13L)).thenReturn(dispatched);
        when(gateway.findCommand("01K5K8VJGR9VK8T3H7ZQX3W1AC")).thenReturn(Optional.of(
                new RobotCommandGateway.RobotCommandReceipt(10L, 4L, 99L, 13L, "request-bound", "MISSION_START", 1)));

        assertThatThrownBy(() -> withGateway.acknowledge(4L, "request-bound", "01K5K8VJGR9VK8T3H7ZQX3W1AD",
                new com.robot.platform.robot.mission.message.MissionAckPayload(13L, "01K5K8VJGR9VK8T3H7ZQX3W1AC", true, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(missions, never()).transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void finalSuccessfulActionCompletesRunningMission() {
        TenantContextHolder.setTenantId(10L);
        RobotCommandGateway gateway = mock(RobotCommandGateway.class);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), gateway, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
        MissionDO running = mission(14L, 10L, 20L, "request-action", "ADMIN", "{}"); running.setStatus("RUNNING");
        com.robot.platform.robot.mission.dal.dataobject.MissionActionDO pending = action(5L, 14L, "PENDING");
        when(robots.selectByTenantAndDeviceId(10L, 4L)).thenReturn(robot(20L, 10L));
        when(missions.selectCurrentById(10L, 14L)).thenReturn(running);
        when(actions.selectOwned(10L, 14L, 5L)).thenReturn(pending);
        when(actions.updateProgress(eq(10L), eq(14L), eq(5L), eq("PENDING"), eq("SUCCESS"), eq(true), any(), isNull(), isNull())).thenReturn(1);
        when(actions.countNotSucceeded(10L, 14L)).thenReturn(0L);
        when(actions.selectByMission(10L, 14L)).thenReturn(List.of(action(5L, 14L, "SUCCESS")));
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(1);

        withGateway.handleAction(4L, "request-action", "01K5K8VJGR9VK8T3H7ZQX3W1AD",
                new com.robot.platform.robot.mission.message.MissionEventPayload("ACTION", 14L, 5L, "SUCCESS", null, null));

        verify(missions).transitionIfVersion(eq(14L), eq(10L), eq("RUNNING"), eq("SUCCESS"), eq(0),
                isNull(), any(), isNull(), isNull());
    }

    @Test
    void failedActionPropagatesAStableFailureToMission() {
        TenantContextHolder.setTenantId(10L);
        MissionServiceImpl withGateway = new MissionServiceImpl(missions, actions, events, executions, robots, liveStatuses,
                new MissionStateMachine(), mock(RobotCommandGateway.class), Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
        MissionDO dispatched = mission(15L, 10L, 20L, "request-failed", "ADMIN", "{}"); dispatched.setStatus("DISPATCHED");
        when(robots.selectByTenantAndDeviceId(10L, 4L)).thenReturn(robot(20L, 10L));
        when(missions.selectCurrentById(10L, 15L)).thenReturn(dispatched);
        when(actions.selectOwned(10L, 15L, 6L)).thenReturn(action(6L, 15L, "PENDING"));
        when(actions.updateProgress(eq(10L), eq(15L), eq(6L), eq("PENDING"), eq("FAILED"), eq(true), any(), eq("MOTOR_FAILURE"), eq("blocked"))).thenReturn(1);
        when(actions.selectList(any())).thenReturn(List.of(action(6L, 15L, "FAILED")));
        when(missions.transitionIfVersion(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), any())).thenReturn(1);

        withGateway.handleAction(4L, "request-failed", "01K5K8VJGR9VK8T3H7ZQX3W1AD",
                new com.robot.platform.robot.mission.message.MissionEventPayload("ACTION", 15L, 6L, "FAILED", "MOTOR_FAILURE", "blocked"));

        verify(missions).transitionIfVersion(eq(15L), eq(10L), eq("DISPATCHED"), eq("FAILED"), eq(0),
                isNull(), any(), eq("MOTOR_FAILURE"), eq("blocked"));
    }

    private static MissionCreateCommand command(long robotId, String requestId, String source) {
        MissionCreateCommand command = new MissionCreateCommand();
        command.setRobotId(robotId); command.setRequestId(requestId); command.setSource(source);
        command.setMissionType("ROUTINE"); command.setPriority(5);
        command.setActions(List.of(new MissionActionCommand("WAIT", "{\"seconds\":5}")));
        return command;
    }

    private static MissionDO mission(long id, long tenantId, long robotId, String requestId, String source, String payload) {
        MissionDO mission = new MissionDO();
        mission.setId(id); mission.setTenantId(tenantId); mission.setRobotId(robotId); mission.setRequestId(requestId);
        mission.setSource(source); mission.setMissionType("ROUTINE"); mission.setPayload(payload); mission.setStatus("PENDING");
        mission.setPriority(1); mission.setVersion(0);
        return mission;
    }

    private static RobotDO robot(long id, long tenantId) {
        return RobotDO.builder().id(id).tenantId(tenantId).deviceId(1L).productId(2L).robotCode("R-1").name("r")
                .onlineStatus("OFFLINE").workStatus("IDLE").build();
    }

    private static com.robot.platform.robot.mission.dal.dataobject.MissionActionDO action(long id, long missionId, String status) {
        com.robot.platform.robot.mission.dal.dataobject.MissionActionDO action = new com.robot.platform.robot.mission.dal.dataobject.MissionActionDO();
        action.setId(id); action.setMissionId(missionId); action.setTenantId(10L); action.setStatus(status);
        return action;
    }
}
