package com.robot.platform.integration;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.common.util.json.JsonUtils;
import com.robot.platform.framework.tenant.core.util.TenantUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.mqtt.RobotMessageEnvelopeCodec;
import com.robot.platform.mqtt.RobotMqttPublisher;
import com.robot.platform.robot.mission.dal.mysql.MissionMapper;
import com.robot.platform.robot.mission.service.MissionService;
import com.robot.platform.robot.mission.service.command.MissionActionCommand;
import com.robot.platform.robot.mission.service.command.MissionCancelCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.command.gateway.RobotCommand;
import com.robot.platform.robot.command.gateway.RobotCommandGateway;
import com.robot.platform.robot.command.outbox.dal.mysql.RobotCommandOutboxMapper;
import com.robot.platform.robot.command.outbox.service.RobotCommandDeliveryFailureHandler;
import com.robot.platform.robot.command.outbox.service.RobotCommandOutboxService;
import com.robot.platform.robot.mission.message.MissionStartPayload;
import com.robot.platform.robot.mission.service.MissionCommandDeliveryFailureHandler;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import org.springframework.dao.DuplicateKeyException;

/** MySQL/MyBatis tenant interceptor proof for Mission reads, cancellation, and Robot ownership. */
@Import(MissionTenantIsolationIT.Ports.class)
@TestPropertySource(properties = "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class MissionTenantIsolationIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private MissionMapper missions;
    @Autowired private MissionService service;
    @Autowired private RobotMapper robots;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingGateway gateway;
    @Autowired private RobotCommandOutboxMapper commandOutbox;
    @Autowired private RobotMessageEnvelopeCodec envelopes;

    @BeforeEach
    void resetGateway() { gateway.commands.clear(); }

    @Test
    void concurrentIdenticalRequestsReturnOneMissionWithOneActionAndEvent() throws Exception {
        long robot = robot(10L, "A-idempotent", 110L);
        List<Object> results = race(() -> missions.selectByRequestId("same-request"),
                () -> service.create(command(robot, "same-request")));
        assertThat(results).allMatch(MissionRespDTO.class::isInstance);
        long id = ((MissionRespDTO) results.get(0)).id();
        assertThat(((MissionRespDTO) results.get(1)).id()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_mission WHERE tenant_id=10", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_mission_action WHERE tenant_id=10", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_mission_event WHERE tenant_id=10", Integer.class)).isEqualTo(1);
        TenantUtils.execute(10L, () -> assertThat(service.create(command(robot, "same-request")).id()).isEqualTo(id));
    }

    @Test
    void concurrentIncompatibleRequestsReturnStableConflict() throws Exception {
        long robot = robot(10L, "A-conflict", 111L);
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        List<Object> results = race(() -> missions.selectByRequestId("conflicting-request"), () -> {
            MissionCreateCommand request = command(robot, "conflicting-request");
            request.setMissionType("TYPE_" + sequence.incrementAndGet());
            return service.create(request);
        });
        assertThat(results.stream().filter(MissionRespDTO.class::isInstance).count()).isEqualTo(1);
        assertThat(results).contains(1_010_004_002);
    }

    @Test
    void repeatedCancellationEnqueuesAndAuditsOnce() {
        long mission = dispatchedMission("repeat-cancel", 112L);
        TenantUtils.execute(10L, () -> {
            service.cancel(mission, new MissionCancelCommand());
            LocalDateTime first = missions.selectById(mission).getCancelRequestedTime();
            service.cancel(mission, new MissionCancelCommand());
            assertThat(missions.selectById(mission).getCancelRequestedTime()).isEqualTo(first);
            assertThat(missions.requestCancelIfVersion(mission, 10L, missions.selectById(mission).getVersion(),
                    first.plusSeconds(1))).isZero();
        });
        assertSingleCancellation(mission);
    }

    @Test
    void concurrentCancellationSucceedsForBothCallersAndEnqueuesOnce() throws Exception {
        long mission = dispatchedMission("concurrent-cancel", 113L);
        assertThat(race(() -> missions.selectById(mission), () -> {
            service.cancel(mission, new MissionCancelCommand()); return true;
        })).containsExactly(true, true);
        assertSingleCancellation(mission);
    }

    /**
     * Real MySQL two-thread proof: after cancellation commits its mission-level fence, a separate
     * stale worker cannot reclaim PUBLISHING. The same fence also rejects an already-SENDING
     * worker at the final broker-publish transition.
     */
    @Test
    void durableCancellationFenceBlocksStaleReclaimAndBrokerPublishTransition() {
        long mission = dispatchedMission("cancel-fence", 115L);
        LocalDateTime stale = LocalDateTime.now().minusMinutes(5).withNano(0);
        jdbc.update("INSERT INTO robot_command_outbox (tenant_id,device_id,robot_id,mission_id,message_id,request_id,topic,envelope,message_type,status,attempt_count,next_attempt_time,claimed_at,deleted) "
                        + "VALUES (10,115,?,?,'start-fence-message','cancel-fence','robot/tenant/product/device/command',JSON_OBJECT(),'MISSION_START','PUBLISHING',1,?,?,b'0')",
                jdbc.queryForObject("SELECT robot_id FROM robot_mission WHERE id=?", Long.class, mission), mission, stale, stale);

        long rowId = rowId(mission);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CountDownLatch cancellationCommitted = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TenantUtils.execute(10L, () -> {
            try {
                Future<Integer> staleReclaim = worker.submit(() -> {
                    assertThat(cancellationCommitted.await(10, TimeUnit.SECONDS)).isTrue();
                    return TenantUtils.execute(10L, () -> new TransactionTemplate(transactions).execute(status ->
                            commandOutbox.markSending(rowId, now, now, now)));
                });
                assertThat(missions.requestCancelIfVersion(mission, 10L, missions.selectById(mission).getVersion(), LocalDateTime.now())).isEqualTo(1);
                cancellationCommitted.countDown();
                assertThat(staleReclaim.get(10, TimeUnit.SECONDS)).isZero();
                jdbc.update("UPDATE robot_command_outbox SET status='SENDING', claimed_at=? WHERE mission_id=?", stale, mission);
                assertThat(commandOutbox.beginPublishing(rowId, stale)).isZero();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            } finally {
                cancellationCommitted.countDown();
                worker.shutdownNow();
            }
            return null;
        });
    }

    /**
     * The production transactional path: eighth broker failure marks its outbox record FAILED and
     * invokes the tenant-aware bridge, which transitions the actual Mission row to FAILED.
     */
    @Test
    void eighthBrokerFailureTransitionsActualMissionThroughDeliveryFailureBridge() {
        long mission = dispatchedMission("broker-exhaustion", 116L);
        insertStartOutbox(mission, "01K5K8VJGR9VK8T3H7ZQX3W1B1", "robot/tenant/product/device/command", 7);
        RobotMqttPublisher publisher = mock(RobotMqttPublisher.class);
        when(publisher.publish(any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        RobotCommandOutboxService outbox = transactionalOutbox(publisher);

        assertThat(outbox.claimAndDispatch(10)).isZero();

        assertThat(jdbc.queryForObject("SELECT status FROM robot_command_outbox WHERE mission_id=?", String.class, mission)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM robot_mission WHERE id=?", String.class, mission)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM robot_mission WHERE id=?", String.class, mission))
                .isEqualTo("MISSION_COMMAND_DELIVERY_EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_mission_event WHERE mission_id=? AND event_type='COMMAND_DELIVERY_EXHAUSTED_MISSION_START'", Integer.class, mission))
                .isEqualTo(1);
    }

    /** The same bridge is used when corrupted pre-publish metadata exhausts its eighth attempt. */
    @Test
    void eighthBadTopicTransitionsActualMissionThroughDeliveryFailureBridge() {
        long mission = dispatchedMission("topic-exhaustion", 117L);
        insertStartOutbox(mission, "01K5K8VJGR9VK8T3H7ZQX3W1B2", "bad/topic", 7);
        RobotMqttPublisher publisher = mock(RobotMqttPublisher.class);

        assertThat(transactionalOutbox(publisher).claimAndDispatch(10)).isZero();

        verifyNoInteractions(publisher);
        assertThat(jdbc.queryForObject("SELECT status FROM robot_command_outbox WHERE mission_id=?", String.class, mission)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM robot_mission WHERE id=?", String.class, mission)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM robot_mission WHERE id=?", String.class, mission))
                .isEqualTo("MISSION_COMMAND_DELIVERY_EXHAUSTED");
    }

    @Test
    void softDeletedActiveMissionReleasesTheRobotSlot() {
        long first = dispatchedMission("deleted-slot", 114L);
        TenantUtils.execute(10L, () -> {
            long robot = missions.selectById(first).getRobotId();
            assertThat(missions.deleteById(first)).isEqualTo(1);
            long second = service.create(command(robot, "replacement-slot")).id();
            assertThat(missions.transitionIfVersion(second, 10L, "PENDING", "DISPATCHED", 0,
                    null, null, null, null)).isEqualTo(1);
        });
    }

    private long dispatchedMission(String requestId, long deviceId) {
        long robot = robot(10L, requestId, deviceId);
        return TenantUtils.execute(10L, () -> {
            long id = service.create(command(robot, requestId)).id();
            assertThat(missions.transitionIfVersion(id, 10L, "PENDING", "DISPATCHED", 0,
                    null, null, null, null)).isEqualTo(1);
            return id;
        });
    }

    private long rowId(long missionId) {
        return jdbc.queryForObject("SELECT id FROM robot_command_outbox WHERE mission_id=?", Long.class, missionId);
    }

    private RobotCommandOutboxService transactionalOutbox(RobotMqttPublisher publisher) {
        @SuppressWarnings("unchecked") ObjectProvider<RobotCommandDeliveryFailureHandler> failures = mock(ObjectProvider.class);
        when(failures.getIfAvailable()).thenReturn(new MissionCommandDeliveryFailureHandler(service));
        return new RobotCommandOutboxService(commandOutbox, publisher, envelopes,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC), transactions, failures, Duration.ofMinutes(2));
    }

    private void insertStartOutbox(long missionId, String messageId, String topic, int attempts) {
        long robotId = jdbc.queryForObject("SELECT robot_id FROM robot_mission WHERE id=?", Long.class, missionId);
        long deviceId = jdbc.queryForObject("SELECT device_id FROM robot WHERE id=?", Long.class, robotId);
        RobotMessageEnvelope<MissionStartPayload> envelope = new RobotMessageEnvelope<>(messageId, "request-" + missionId,
                1757894400000L, 1, MessageType.MISSION_START, MessageSource.CLOUD,
                new MissionStartPayload(missionId, JsonUtils.parseObject("{}", JsonNode.class)));
        jdbc.update("INSERT INTO robot_command_outbox (tenant_id,device_id,robot_id,mission_id,message_id,request_id,topic,envelope,message_type,status,attempt_count,next_attempt_time,deleted) "
                        + "VALUES (10,?,?,?,?,?,?,CAST(? AS JSON),'MISSION_START','PENDING',?,?,b'0')",
                deviceId, robotId, missionId, messageId, "request-" + missionId, topic,
                new String(envelopes.encode(envelope), StandardCharsets.UTF_8), attempts, LocalDateTime.of(2026, 9, 14, 23, 59));
    }

    private void assertSingleCancellation(long id) {
        assertThat(gateway.commands).hasSize(1);
        assertThat(gateway.commands.get(0).missionId()).isEqualTo(id);
        assertThat(gateway.commands.get(0).type()).isEqualTo("MISSION_CANCEL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_mission_event WHERE tenant_id=10 AND mission_id=? AND event_type='CANCEL_REQUESTED'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM robot_mission WHERE tenant_id=10 AND id=?", Integer.class, id)).isEqualTo(2);
    }

    /** Establish both RR snapshots before either operation: deterministic real InnoDB contention. */
    private List<Object> race(Runnable snapshot, Supplier<?> operation) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Callable<Object> work = () -> TenantUtils.execute(10L, () -> {
            try {
                return new TransactionTemplate(transactions).execute(status -> {
                    snapshot.run();
                    try { bothRead.await(10, TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                    return operation.get();
                });
            } catch (ServiceException conflict) {
                return conflict.getCode();
            }
        });
        try {
            Future<Object> first = workers.submit(work);
            Future<Object> second = workers.submit(work);
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void tenantACannotReadCancelOrTargetTenantBRobotOrMission() {
        long robotB = robot(20L, "B-mission", 200L);
        long missionB = TenantUtils.execute(20L, () -> service.create(command(robotB, "request-b")).id());

        TenantUtils.execute(10L, () -> {
            assertThat(missions.selectById(missionB)).isNull();
            assertThatThrownBy(() -> service.get(missionB)).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.cancel(missionB, new MissionCancelCommand())).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.create(command(robotB, "request-a-foreign"))).isInstanceOf(ServiceException.class);
        });

        TenantUtils.execute(20L, () -> assertThat(missions.selectById(missionB)).isNotNull());
    }

    @Test
    void databaseAllowsOnlyOneActiveDispatchSlotPerRobot() {
        long robot = robot(10L, "A-slot", 101L);
        long first = TenantUtils.execute(10L, () -> service.create(command(robot, "request-slot-1")).id());
        long second = TenantUtils.execute(10L, () -> service.create(command(robot, "request-slot-2")).id());

        TenantUtils.execute(10L, () -> {
            assertThat(missions.transitionIfVersion(first, 10L, "PENDING", "DISPATCHED", 0,
                    null, null, null, null)).isEqualTo(1);
            assertThatThrownBy(() -> missions.transitionIfVersion(second, 10L, "PENDING", "DISPATCHED", 0,
                    null, null, null, null)).isInstanceOf(DuplicateKeyException.class);
        });
    }

    private long robot(long tenantId, String code, long deviceId) {
        return TenantUtils.execute(tenantId, () -> {
            RobotDO robot = RobotDO.builder().tenantId(tenantId).deviceId(deviceId).productId(3L).robotCode(code)
                    .name(code).onlineStatus("OFFLINE").workStatus("IDLE").build();
            robots.insert(robot); return robot.getId();
        });
    }

    private static MissionCreateCommand command(long robotId, String requestId) {
        MissionCreateCommand command = new MissionCreateCommand();
        command.setRobotId(robotId); command.setMissionType("ROUTINE"); command.setSource("ADMIN");
        command.setPriority(1); command.setRequestId(requestId);
        command.setActions(List.of(new MissionActionCommand("WAIT", "{\"seconds\":1}")));
        return command;
    }

    @TestConfiguration
    @MapperScan(basePackages = {
            "com.robot.platform.robot.robot.dal.mysql", "com.robot.platform.robot.mission.dal.mysql",
            "com.robot.platform.robot.command.outbox.dal.mysql",
            "com.robot.platform.robot.message.inbox.dal.mysql", "com.robot.platform.robot.realtime.outbox.dal.mysql",
            "com.robot.platform.device.device.dal.mysql", "com.robot.platform.device.group.dal.mysql",
            "com.robot.platform.device.product.dal.mysql", "com.robot.platform.tenant.quota.dal.mysql",
            "com.robot.platform.member.member.dal.mysql", "com.robot.platform.member.binding.dal.mysql"
    })
    static class Ports {
        @Bean RecordingGateway commandGateway() { return new RecordingGateway(); }
    }

    static class RecordingGateway implements RobotCommandGateway {
        final List<RobotCommand> commands = new CopyOnWriteArrayList<>();
        @Override public void enqueue(RobotCommand command) { commands.add(command); }
    }
}
