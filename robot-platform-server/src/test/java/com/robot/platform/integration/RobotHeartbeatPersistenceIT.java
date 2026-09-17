package com.robot.platform.integration;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.dal.mysql.RobotMessageInboxMapper;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import com.robot.platform.robot.status.service.RobotLiveStatusQueryService;
import com.robot.platform.robot.status.job.RobotOfflineJob;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.outbox.RobotRealtimeEventOutboxService;
import com.robot.platform.robot.realtime.outbox.dal.mysql.RobotRealtimeEventOutboxMapper;
import com.robot.platform.robot.status.service.RobotOfflineTransitionService;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import com.robot.platform.server.RobotPlatformApplication;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL/Redis persistence proof; the Redis spy only injects selected failures, all successful I/O is real. */
@Import(RobotHeartbeatPersistenceIT.Ports.class)
@TestPropertySource(properties = {"robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "robot.realtime.outbox-scan-interval=PT1H"})
@SpringBootTest(classes = RobotPlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RobotHeartbeatPersistenceIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private RobotMapper robots;
    @Autowired private RobotHeartbeatService heartbeats;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private RobotLiveStatusStore statuses;
    @Autowired private RobotLiveStatusQueryService statusQueries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RobotOfflineTransitionService offlineTransitions;
    @Autowired private RobotRealtimeEventOutboxMapper realtimeOutbox;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private WebSocketMessageSender websocket;

    @Test void nullableHeartbeatTelemetryClearsMysqlAndRedisMissFallbackWithoutRepeatedSnapshots() {
        long robotId = insertHeartbeatRobot("ONLINE");
        jdbc.update("UPDATE robot SET current_mission_id='M-1', software_version='1.0', ip_address='127.0.0.1', "
                + "last_heartbeat_time=? WHERE id=?", LocalDateTime.now(ZoneOffset.UTC), robotId);
        var first = new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "CLEAR-NULLS", Instant.now().toEpochMilli(), 1,
                MessageType.HEARTBEAT, MessageSource.ROBOT,
                new HeartbeatPayload(robotId, 70, 20, 30, 25, "IDLE", "127.0.0.1", null, null));

        assertThat(heartbeats.accept(heartbeatIdentity(robotId), first)).isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);
        RobotDO cleared = robots.selectByTenantAndId(10, robotId);
        assertThat(cleared.getCurrentMissionId()).isNull();
        assertThat(cleared.getSoftwareVersion()).isNull();
        redis.delete(RobotLiveStatusStore.key(10, robotId));
        RobotLiveStatus fallback = statusQueries.find(10, robotId).orElseThrow();
        assertThat(fallback.currentMissionId()).isNull();
        assertThat(fallback.softwareVersion()).isNull();
        // Battery alone is deliberately not material. A repeated null mission/version must not
        // trigger another snapshot; changing battery makes an unwanted UPDATE observable.
        var repeated = new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MP", "CLEAR-NULLS", Instant.now().toEpochMilli(), 1,
                MessageType.HEARTBEAT, MessageSource.ROBOT,
                new HeartbeatPayload(robotId, 71, 20, 30, 25, "IDLE", "127.0.0.1", null, null));
        heartbeats.accept(heartbeatIdentity(robotId), repeated);
        RobotDO saved = robots.selectByTenantAndId(10, robotId);
        assertThat(saved.getBatteryLevel()).isEqualTo(70);
        assertThat(saved.getLastHeartbeatTime()).isEqualTo(cleared.getLastHeartbeatTime());
        assertThat(statuses.get(10, robotId).orElseThrow().batteryLevel()).isEqualTo(71);
    }

    @Test void redisGetFailureDuringOfflineCommitIsCorrectedOnReadEvenWithANewerThrottledProjection() {
        long robotId = insertHeartbeatRobot("ONLINE");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant snapshotAt = now.minusSeconds(240);
        Instant cachedAt = snapshotAt.plusSeconds(30);
        jdbc.update("UPDATE robot SET last_heartbeat_time=? WHERE id=?", LocalDateTime.ofInstant(snapshotAt, ZoneOffset.UTC), robotId);
        RobotLiveStatus cached = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                70, 20, 30, 25, "127.0.0.1", null, "1.0", cachedAt, cachedAt.toEpochMilli());
        statuses.put(10, robotId, cached, Duration.ofMinutes(10));
        statuses.put(20, robotId, cached, Duration.ofMinutes(10));
        org.mockito.Mockito.doThrow(new IllegalStateException("injected one-shot GET failure"))
                .doCallRealMethod().when(statuses).get(10, robotId);

        assertThat(TenantUtils.execute(10L, () -> offlineTransitions.persist(robots.selectByTenantAndId(10, robotId),
                now.minusSeconds(120), now, Duration.ofMinutes(10)))).isTrue();
        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("OFFLINE");
        assertThat(statuses.get(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.ONLINE);
        // Repair may fail independently too: correctness survives, and the next read retries it.
        org.mockito.Mockito.doThrow(new IllegalStateException("injected one-shot CAS failure"))
                .doCallRealMethod().when(statuses).transitionOffline(10, robotId, 1, cachedAt, cachedAt, Duration.ofMinutes(10));
        assertThat(statusQueries.find(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
        assertThat(statuses.get(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.ONLINE);
        assertThat(statusQueries.find(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
        assertThat(statuses.get(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
        assertThat(statuses.get(20, robotId).orElseThrow()).isEqualTo(cached);
        assertThat(statusQueries.find(20, robotId)).isEmpty();
    }

    @Test void statusQueryWaitsForInFlightHeartbeatCommitAndPreservesItsFreshRedisProjection() throws Exception {
        long robotId = insertHeartbeatRobot("OFFLINE");
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var heartbeat = MYSQL.createConnection("");
             var observer = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())) {
            heartbeat.setAutoCommit(false);
            try {
                Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                // Hold the exact row lock acquired by accept(), then expose Redis before commit.
                try (var update = heartbeat.prepareStatement("UPDATE robot SET online_status='ONLINE',last_heartbeat_time=? WHERE id=?")) {
                    update.setObject(1, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
                    update.setLong(2, robotId);
                    update.executeUpdate();
                }
                RobotLiveStatus fresh = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                        81, 20, 30, 25, "127.0.0.1", "M-2", "1.1", now, now.toEpochMilli());
                statuses.put(10, robotId, fresh, Duration.ofMinutes(10));
                var query = executor.submit(() -> statusQueries.find(10, robotId));
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
                    try (var statement = observer.createStatement(); var result = statement.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'")) {
                        result.next();
                        return result.getInt(1) > 0;
                    }
                });
                heartbeat.commit();
                assertThat(query.get(10, java.util.concurrent.TimeUnit.SECONDS)).contains(fresh);
                assertThat(statuses.get(10, robotId).orElseThrow()).isEqualTo(fresh);
            } finally {
                heartbeat.rollback();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void afterCommitDeliverySuspendsCommittedTransactionAndFailureLeavesDurableRetry() {
        var activeTransactionDuringSend = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            activeTransactionDuringSend.set(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive());
            throw new IllegalStateException("websocket unavailable after heartbeat commit");
        }).when(websocket).send(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        long robotId = insertHeartbeatRobot("OFFLINE");

        assertThat(heartbeats.accept(heartbeatIdentity(robotId),
                heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MN", "IDLE")))
                .isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);

        assertThat(activeTransactionDuringSend).isFalse();
        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("ONLINE");
        assertThat(jdbc.queryForObject("SELECT status FROM robot_realtime_event_outbox WHERE robot_id=?", String.class, robotId))
                .isEqualTo("RETRY");
    }

    @Test void differentHeartbeatMessagesSharingRequestIdKeepDistinctLifecycleEvents() {
        long robotId = insertHeartbeatRobot("OFFLINE");
        var identity = heartbeatIdentity(robotId);
        heartbeats.accept(identity, heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MN", "IDLE"));
        heartbeats.accept(identity, heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MP", "WORKING"));
        heartbeats.accept(identity, heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MQ", "IDLE"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox WHERE robot_id=?", Long.class, robotId))
                .isEqualTo(3L);
    }

    @Test void heartbeatReadsTheLockedLatestStateAndEmitsOnlineAfterConcurrentOfflineCommit() throws Exception {
        long robotId = insertHeartbeatRobot("ONLINE");
        jdbc.update("UPDATE robot SET last_heartbeat_time=? WHERE id=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3), robotId);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var offline = MYSQL.createConnection("");
             var observer = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())) {
            offline.setAutoCommit(false);
            try {
                try (var update = offline.prepareStatement("UPDATE robot SET online_status='OFFLINE' WHERE id=?")) {
                    update.setLong(1, robotId);
                    update.executeUpdate();
                }
                var heartbeat = executor.submit(() -> heartbeats.accept(heartbeatIdentity(robotId),
                        heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MN", "IDLE")));
                // Both implementations reach a real InnoDB lock wait: the old one only at UPDATE,
                // after reading ONLINE; the corrected one at SELECT FOR UPDATE, before reading state.
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
                    try (var query = observer.createStatement(); var result = query.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'")) {
                        result.next();
                        return result.getInt(1) > 0;
                    }
                });
                offline.commit();
                assertThat(heartbeat.get(10, java.util.concurrent.TimeUnit.SECONDS))
                        .isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);
                assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("ONLINE");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox WHERE robot_id=?", Long.class, robotId))
                        .isEqualTo(1L);
            } finally {
                offline.rollback();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void heartbeatWinningAfterScannerObservationPreventsOfflineWithThrottledDatabaseSnapshot() {
        long robotId = insertHeartbeatRobot("ONLINE");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant staleAt = now.minusSeconds(180);
        jdbc.update("UPDATE robot SET last_heartbeat_time=? WHERE id=?", LocalDateTime.ofInstant(staleAt, ZoneOffset.UTC), robotId);
        RobotLiveStatus observed = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                70, 20, 30, 25, "127.0.0.1", null, "1.0", staleAt, staleAt.toEpochMilli());
        RobotLiveStatus fresh = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                72, 20, 30, 25, "127.0.0.1", null, "1.0", now, now.toEpochMilli());
        statuses.put(10, robotId, fresh, Duration.ofMinutes(10));

        assertThat(TenantUtils.execute(10L, () -> offlineTransitions.persist(
                robots.selectByTenantAndId(10, robotId), now.minusSeconds(120), now, Duration.ofMinutes(10))))
                .isFalse();
        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("ONLINE");
        assertThat(statuses.get(10, robotId).orElseThrow()).isEqualTo(fresh);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox", Long.class)).isZero();
    }

    @Test void outerTransactionRollbackRemovesStateInboxAndEventAndNextHeartbeatRepairsRedisSideEffect() {
        long robotId = insertHeartbeatRobot("OFFLINE");
        var identity = heartbeatIdentity(robotId);
        var message = heartbeatMessage(robotId, "01J0A1B2C3D4E5F6G7H8J9K0MN", "IDLE");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            heartbeats.accept(identity, message);
            throw new IllegalStateException("rollback after event enqueue");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("OFFLINE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_message_inbox", Long.class)).isZero();
        assertThat(statuses.get(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.ONLINE);

        assertThat(heartbeats.accept(identity, message)).isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);
        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("ONLINE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox", Long.class)).isEqualTo(1L);
    }

    private long insertHeartbeatRobot(String onlineStatus) {
        return TenantUtils.execute(10L, () -> {
            RobotDO robot = RobotDO.builder().tenantId(10L).deviceId(40006L).productId(3L)
                    .robotCode("HB-EVENT").name("Heartbeat events").onlineStatus(onlineStatus).workStatus("IDLE").build();
            robots.insert(robot);
            return robot.getId();
        });
    }

    private DeviceMqttIdentity heartbeatIdentity(long robotId) {
        return new DeviceMqttIdentity(40006, 10, robotId, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);
    }

    private RobotMessageEnvelope<HeartbeatPayload> heartbeatMessage(long robotId, String messageId, String workStatus) {
        return new RobotMessageEnvelope<>(messageId, "SHARED-REQUEST", Instant.now().toEpochMilli(), 1,
                MessageType.HEARTBEAT, MessageSource.ROBOT,
                new HeartbeatPayload(robotId, 70, 20, 30, 25, workStatus, "127.0.0.1", null, "1.0"));
    }

    @Test void mysqlInboxArbitratesDuplicatesBeforeTenantScopedRedisStatus() {
        long robotId = TenantUtils.execute(10L, () -> { RobotDO robot = RobotDO.builder().tenantId(10L).deviceId(4L).productId(3L)
                .robotCode("HB-1").name("HB-1").onlineStatus("OFFLINE").workStatus("IDLE").build(); robots.insert(robot); return robot.getId(); });
        var identity = new DeviceMqttIdentity(4, 10, robotId, "tenant-a", "product", "SN-1", "tenant-a/product/SN-1", "hash", 1);
        var message = new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "REQ-1", Instant.now().toEpochMilli(), 1,
                MessageType.HEARTBEAT, MessageSource.ROBOT, new HeartbeatPayload(robotId, 70, 20, 30, 25, "IDLE", "127.0.0.1", null, "1.0"));

        assertThat(heartbeats.accept(identity, message)).isEqualTo(RobotHeartbeatService.AcceptResult.ACCEPTED);
        assertThat(heartbeats.accept(identity, message)).isEqualTo(RobotHeartbeatService.AcceptResult.DUPLICATE);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_message_inbox WHERE tenant_id=10 AND device_id=4", Long.class)).isEqualTo(1L);
        assertThat(redis.hasKey(RobotLiveStatusStore.key(10, robotId))).isTrue();
        assertThat(statuses.get(10, robotId)).isPresent();
        assertThat(jdbc.queryForObject("SELECT status FROM robot_realtime_event_outbox WHERE robot_id=?", String.class, robotId))
                .isIn("SENT", "RETRY");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM robot_realtime_event_outbox WHERE robot_id=?", Integer.class, robotId))
                .isEqualTo(1);
    }

    @Test void redisOfflineLuaComparesSchemaObservedHeartbeatAndCutoff() {
        Instant heartbeatAt = Instant.parse("2026-09-13T12:00:00Z");
        RobotLiveStatus online = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                70, 20, 30, 25, "127.0.0.1", null, "1.0", heartbeatAt, heartbeatAt.toEpochMilli());
        statuses.put(10, 700, online, Duration.ofMinutes(10));

        assertThat(statuses.transitionOffline(10, 700, 2, heartbeatAt, heartbeatAt, Duration.ofMinutes(10))).isFalse();
        assertThat(statuses.transitionOffline(10, 700, 1, heartbeatAt.minusMillis(1), heartbeatAt, Duration.ofMinutes(10))).isFalse();
        assertThat(statuses.transitionOffline(10, 700, 1, heartbeatAt, heartbeatAt.minusMillis(1), Duration.ofMinutes(10))).isFalse();
        assertThat(statuses.transitionOffline(10, 700, 1, heartbeatAt, heartbeatAt, Duration.ofMinutes(10))).isTrue();
        assertThat(statuses.get(10, 700).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
    }

    @Test void redisMissingProjectionRestoreNeverOverwritesAConcurrentHeartbeat() {
        Instant staleAt = Instant.parse("2026-09-13T12:00:00Z");
        Instant freshAt = staleAt.plusSeconds(180);
        RobotLiveStatus freshOnline = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                81, 12, 24, 23, "127.0.0.2", null, "1.1", freshAt, freshAt.toEpochMilli());
        RobotLiveStatus staleOffline = new RobotLiveStatus(1, RobotOnlineStatus.OFFLINE, RobotWorkStatus.IDLE,
                70, 20, 30, 25, "127.0.0.1", null, "1.0", staleAt, staleAt.toEpochMilli());
        statuses.put(10, 701, freshOnline, Duration.ofMinutes(10));

        assertThat(statuses.restoreOfflineIfAbsent(10, 701, staleOffline, Duration.ofMinutes(10))).isFalse();
        assertThat(statuses.get(10, 701).orElseThrow()).isEqualTo(freshOnline);
    }

    @Test void lockedOfflineRevalidationKeepsFreshHeartbeatOnlineWithoutAnOfflineEvent() {
        Instant now = Instant.parse("2026-09-13T12:10:00Z");
        Instant staleAt = now.minus(Duration.ofMinutes(3));
        long robotId = TenantUtils.execute(10L, () -> {
            RobotDO robot = RobotDO.builder().tenantId(10L).deviceId(40_005L).productId(3L)
                    .robotCode("HB-RACE-" + System.nanoTime()).name("HB race").onlineStatus("ONLINE")
                    .workStatus("IDLE").batteryLevel(66)
                    .lastHeartbeatTime(LocalDateTime.ofInstant(staleAt, ZoneOffset.UTC)).build();
            robots.insert(robot); return robot.getId();
        });
        Instant freshAt = now.plusSeconds(1);
        RobotLiveStatus fresh = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.WORKING,
                90, 10, 20, 22, "127.0.0.2", "M-2", "1.1", freshAt, freshAt.toEpochMilli());
        // The scanner candidate was stale, but Redis was refreshed before its locked revalidation.
        // A stale candidate must never create an OFFLINE state/event after that heartbeat.
        statuses.put(10, robotId, fresh, Duration.ofMinutes(10));

        assertThat(TenantUtils.execute(10L, () -> offlineTransitions.persist(robots.selectByTenantAndId(10, robotId),
                now.minus(Duration.ofMinutes(2)), now, Duration.ofMinutes(10)))).isFalse();

        assertThat(robots.selectByTenantAndId(10, robotId).getOnlineStatus()).isEqualTo("ONLINE");
        assertThat(statuses.get(10, robotId).orElseThrow()).isEqualTo(fresh);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox WHERE robot_id=?", Long.class, robotId))
                .isZero();
    }

    @Test void failedRealtimePublishRetriesOneStableLogicalEventFromMySql() {
        WebSocketMessageSender sender = org.mockito.Mockito.mock(WebSocketMessageSender.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis pubsub down")).doNothing().when(sender)
                .send(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        org.springframework.beans.factory.support.StaticListableBeanFactory beans =
                new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("failingOnceSender", sender);
        Instant now = Instant.parse("2026-09-13T12:20:00Z");
        RobotRealtimeEventOutboxService service = new RobotRealtimeEventOutboxService(realtimeOutbox,
                beans.getBeanProvider(WebSocketMessageSender.class), Clock.fixed(now, ZoneOffset.UTC),
                Duration.ZERO, Duration.ofMinutes(2));
        RobotLiveStatus live = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                72, 20, 30, 25, "127.0.0.1", null, "1.0", now, now.toEpochMilli());
        TenantRobotRealtimeEvent event = new TenantRobotRealtimeEvent(1, 10, 70_001, "REQ-OUTBOX-1",
                "ROBOT_STATUS_CHANGED", now, live);

        long id = TenantUtils.execute(10L, () -> service.enqueue(event));
        long duplicateId = TenantUtils.execute(10L, () -> service.enqueue(event));
        assertThat(duplicateId).isEqualTo(id);
        service.dispatchSafely(id);
        service.dispatchSafely(id);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM robot_realtime_event_outbox WHERE id=?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM robot_realtime_event_outbox WHERE id=?", String.class, id)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM robot_realtime_event_outbox WHERE id=?", Integer.class, id)).isEqualTo(2);
        org.mockito.Mockito.verify(sender, org.mockito.Mockito.times(2)).send(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq("ROBOT_STATUS_CHANGED"),
                org.mockito.ArgumentMatchers.contains("REQ-OUTBOX-1"));
    }

    @Test void expiredOutboxClaimCanBeRecoveredWithoutAnOldWorkerCompletingItsReplacement() {
        LocalDateTime first = LocalDateTime.of(2026, 9, 13, 12, 0);
        jdbc.update("INSERT INTO robot_realtime_event_outbox (tenant_id,robot_id,event_key,event_type,payload,next_attempt_time) "
                + "VALUES (10,71000,'lease-test','ROBOT_STATUS_CHANGED','{}',?)", first);
        long id = jdbc.queryForObject("SELECT id FROM robot_realtime_event_outbox WHERE event_key='lease-test'", Long.class);

        assertThat(realtimeOutbox.claim(id, first, first.minusMinutes(2))).isEqualTo(1);
        assertThat(realtimeOutbox.claim(id, first.plusMinutes(1), first.minusMinutes(1))).isZero();
        LocalDateTime replacement = first.plusMinutes(3);
        assertThat(realtimeOutbox.claim(id, replacement, first.plusMinutes(1))).isEqualTo(1);
        assertThat(realtimeOutbox.markSent(id, first)).isZero();
        assertThat(realtimeOutbox.markRetry(id, first, replacement.plusSeconds(5), "stale failure")).isZero();
        assertThat(realtimeOutbox.markSent(id, replacement)).isEqualTo(1);
        assertThat(realtimeOutbox.claim(id, replacement.plusHours(1), replacement)).isZero();
    }

    @Test void missingRedisProjectionFallsBackToMySqlAndOfflineScanRepairsIt() {
        Instant now = Instant.parse("2026-09-13T12:10:00Z");
        long robotId = TenantUtils.execute(10L, () -> {
            RobotDO robot = RobotDO.builder().tenantId(10L).deviceId(40_004L).productId(3L)
                    .robotCode("HB-MISSING-" + System.nanoTime()).name("HB missing").onlineStatus("ONLINE")
                    .workStatus("IDLE").batteryLevel(66).lastHeartbeatTime(
                            LocalDateTime.ofInstant(now.minus(Duration.ofMinutes(3)), ZoneOffset.UTC)).build();
            robots.insert(robot);
            return robot.getId();
        });
        redis.delete(RobotLiveStatusStore.key(10, robotId));

        assertThat(statusQueries.find(10, robotId)).isPresent();
        RobotRealtimeEventPublisher events = org.mockito.Mockito.mock(RobotRealtimeEventPublisher.class);
        new RobotOfflineJob(robots, statuses, Duration.ofMinutes(10), Duration.ofMinutes(2),
                new RobotOfflineTransitionService(robots, statuses, events),
                Clock.fixed(now, ZoneOffset.UTC)).scan();

        RobotDO saved = robots.selectByTenantAndId(10, robotId);
        assertThat(saved.getOnlineStatus()).isEqualTo("OFFLINE");
        assertThat(statuses.get(10, robotId).orElseThrow().onlineStatus()).isEqualTo(RobotOnlineStatus.OFFLINE);
        org.mockito.Mockito.verify(events).publish(org.mockito.ArgumentMatchers.argThat(event -> event.robotId() == robotId));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @MapperScan(basePackages = {"com.robot.platform.robot.robot.dal.mysql", "com.robot.platform.robot.message.inbox.dal.mysql",
            "com.robot.platform.robot.realtime.outbox.dal.mysql",
            "com.robot.platform.tenant.quota.dal.mysql", "com.robot.platform.device.device.dal.mysql", "com.robot.platform.device.group.dal.mysql",
            "com.robot.platform.device.product.dal.mysql", "com.robot.platform.member.member.dal.mysql", "com.robot.platform.member.binding.dal.mysql"})
    static class Ports { }
}
