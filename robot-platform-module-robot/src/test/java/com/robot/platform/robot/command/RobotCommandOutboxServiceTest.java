package com.robot.platform.robot.command;

import com.robot.platform.mqtt.RobotMqttPublisher;
import com.robot.platform.mqtt.RobotMessageEnvelopeCodec;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.command.outbox.dal.mysql.RobotCommandOutboxMapper;
import com.robot.platform.robot.command.outbox.service.RobotCommandOutboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The production change this catches: a successful broker acknowledgement leaves an outbox row retryable. */
class RobotCommandOutboxServiceTest {
    private final RobotCommandOutboxMapper mapper = mock(RobotCommandOutboxMapper.class);
    private final RobotMqttPublisher publisher = mock(RobotMqttPublisher.class);
    private final RobotMessageEnvelopeCodec envelopes = mock(RobotMessageEnvelopeCodec.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void brokerAcknowledgementMarksOnlyClaimedCommandSent() {
        RobotCommandOutboxDO row = command(7L);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(7L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.beginPublishing(7L, row.getClaimedAt())).thenReturn(1);
        when(mapper.markSent(7L, row.getClaimedAt())).thenReturn(1);
        doAnswer(invocation -> new com.robot.platform.mqtt.RobotMessageEnvelope<>(row.getMessageId(), row.getRequestId(),
                1757894400000L, 1, com.robot.platform.mqtt.MessageType.MISSION_START, com.robot.platform.mqtt.MessageSource.CLOUD, new Object()))
                .when(envelopes).decode(any(), isNull());
        when(publisher.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        RobotCommandOutboxService service = new RobotCommandOutboxService(mapper, publisher, envelopes, clock);

        int delivered = service.claimAndDispatch(10);

        assertThat(delivered).isEqualTo(1);
        verify(mapper).markSent(7L, row.getClaimedAt());
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
    }

    @Test
    void brokerFailureSchedulesBoundedRetryInsteadOfMarkingCommandSent() {
        RobotCommandOutboxDO row = command(8L);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(8L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.beginPublishing(8L, row.getClaimedAt())).thenReturn(1);
        doAnswer(invocation -> new com.robot.platform.mqtt.RobotMessageEnvelope<>(row.getMessageId(), row.getRequestId(),
                1757894400000L, 1, com.robot.platform.mqtt.MessageType.MISSION_START, com.robot.platform.mqtt.MessageSource.CLOUD, new Object()))
                .when(envelopes).decode(any(), isNull());
        when(publisher.publish(any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int delivered = new RobotCommandOutboxService(mapper, publisher, envelopes, clock).claimAndDispatch(10);

        assertThat(delivered).isZero();
        verify(mapper).markRetry(eq(8L), eq(row.getClaimedAt()), eq("RETRY"), eq(row.getClaimedAt().plusSeconds(1)), contains("broker down"));
        verify(mapper, never()).markSent(8L, row.getClaimedAt());
    }

    /** The production change this catches: an unreachable broker leaves a command retrying forever. */
    @Test
    void eighthFailedAttemptIsExhaustedInsteadOfBeingScheduledAgain() {
        RobotCommandOutboxDO row = command(9L); row.setAttemptCount(7);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(9L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.beginPublishing(9L, row.getClaimedAt())).thenReturn(1);
        doAnswer(invocation -> new com.robot.platform.mqtt.RobotMessageEnvelope<>(row.getMessageId(), row.getRequestId(),
                1757894400000L, 1, com.robot.platform.mqtt.MessageType.MISSION_START, com.robot.platform.mqtt.MessageSource.CLOUD, new Object()))
                .when(envelopes).decode(any(), isNull());
        when(publisher.publish(any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(new RobotCommandOutboxService(mapper, publisher, envelopes, clock).claimAndDispatch(10)).isZero();

        verify(mapper).markRetry(eq(9L), eq(row.getClaimedAt()), eq("FAILED"), eq(row.getClaimedAt()), contains("broker down"));
    }

    /**
     * The production change this catches: a worker checks its lease, cancellation invalidates the
     * command, then that old worker publishes anyway.  Decoding deliberately pauses before the
     * irreversible broker phase so this is a deterministic interleaving, not a timing test.
     */
    @Test
    void cancellationBeforeIrreversiblePublishPreventsClaimedStartFromBeingPublished() throws Exception {
        RobotCommandOutboxDO row = command(10L);
        CountDownLatch readyToPublish = new CountDownLatch(1);
        CountDownLatch continueWorker = new CountDownLatch(1);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(10L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.invalidateUndeliveredStarts(10L, 40L)).thenReturn(1);
        when(mapper.beginPublishing(10L, row.getClaimedAt())).thenReturn(0);
        doAnswer(invocation -> {
            readyToPublish.countDown();
            assertThat(continueWorker.await(5, TimeUnit.SECONDS)).isTrue();
            return new com.robot.platform.mqtt.RobotMessageEnvelope<>(row.getMessageId(), row.getRequestId(),
                    1757894400000L, 1, com.robot.platform.mqtt.MessageType.MISSION_START,
                    com.robot.platform.mqtt.MessageSource.CLOUD, new Object());
        }).when(envelopes).decode(any(), isNull());
        when(publisher.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        RobotCommandOutboxService service = new RobotCommandOutboxService(mapper, publisher, envelopes, clock);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Integer> dispatched = CompletableFuture.supplyAsync(() -> service.claimAndDispatch(10), worker);
            assertThat(readyToPublish.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.invalidateUndeliveredStarts(10L, 40L))
                    .extracting(com.robot.platform.robot.command.gateway.RobotCommandGateway.StartCancellation::outcome)
                    .isEqualTo(com.robot.platform.robot.command.gateway.RobotCommandGateway.StartCancellationOutcome.INVALIDATED);
            continueWorker.countDown();
            assertThat(dispatched.get(5, TimeUnit.SECONDS)).isZero();
            verify(publisher, never()).publish(any(), any());
            verify(mapper).beginPublishing(10L, row.getClaimedAt());
        } finally {
            continueWorker.countDown();
            worker.shutdownNow();
        }
    }

    /** Once PUBLISHING wins the atomic race, MISSION_CANCEL is explicit compensation, not a retraction. */
    @Test
    void cancellationAfterIrreversiblePublishStageUsesCompensationAndDoesNotSuppressPublish() {
        RobotCommandOutboxDO row = command(11L);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(11L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.beginPublishing(11L, row.getClaimedAt())).thenReturn(1);
        doAnswer(invocation -> new com.robot.platform.mqtt.RobotMessageEnvelope<>(row.getMessageId(), row.getRequestId(),
                1757894400000L, 1, com.robot.platform.mqtt.MessageType.MISSION_START,
                com.robot.platform.mqtt.MessageSource.CLOUD, new Object())).when(envelopes).decode(any(), isNull());
        RobotCommandOutboxService service = new RobotCommandOutboxService(mapper, publisher, envelopes, clock);
        doAnswer(invocation -> {
            assertThat(service.invalidateUndeliveredStarts(10L, 40L))
                    .extracting(com.robot.platform.robot.command.gateway.RobotCommandGateway.StartCancellation::outcome)
                    .isEqualTo(com.robot.platform.robot.command.gateway.RobotCommandGateway.StartCancellationOutcome.COMPENSATING);
            return CompletableFuture.completedFuture(null);
        }).when(publisher).publish(any(), any());
        when(mapper.markIrreversibleStartForCancellation(10L, 40L)).thenReturn(1);

        assertThat(service.claimAndDispatch(10)).isZero();
        verify(publisher).publish(any(), any());
        verify(mapper).markIrreversibleStartForCancellation(10L, 40L);
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
    }

    /** Bad persisted transport metadata must exhaust too; it never reaches the PUBLISHING mapper path. */
    @Test
    void eighthBadTopicFailureIsExhaustedFromSendingStage() {
        RobotCommandOutboxDO row = command(12L); row.setAttemptCount(7); row.setTopic("not/a/robot/command/topic");
        com.robot.platform.robot.command.outbox.service.RobotCommandDeliveryFailureHandler exhausted = mock(com.robot.platform.robot.command.outbox.service.RobotCommandDeliveryFailureHandler.class);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(12L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(mapper.markPrePublishFailure(eq(12L), eq(row.getClaimedAt()), eq("FAILED"), eq(row.getClaimedAt()), contains("topic"))).thenReturn(1);

        assertThat(new RobotCommandOutboxService(mapper, publisher, envelopes, clock, exhausted).claimAndDispatch(10)).isZero();

        verify(mapper).markPrePublishFailure(eq(12L), eq(row.getClaimedAt()), eq("FAILED"), eq(row.getClaimedAt()), contains("topic"));
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
        verify(exhausted).exhausted(eq(row), eq("MISSION_COMMAND_DELIVERY_EXHAUSTED"), contains("invalid topic"));
    }

    /** A malformed durable envelope is also pre-publish work and must not reclaim forever. */
    @Test
    void eighthBadEnvelopeFailureIsExhaustedFromSendingStage() {
        RobotCommandOutboxDO row = command(13L); row.setAttemptCount(7);
        when(mapper.lockDueForDispatch(any(), any(), eq(10))).thenReturn(List.of(row));
        when(mapper.markSending(13L, row.getClaimedAt(), row.getClaimedAt(), row.getClaimedAt().minusMinutes(2))).thenReturn(1);
        when(envelopes.decode(any(), isNull())).thenReturn(null);

        assertThat(new RobotCommandOutboxService(mapper, publisher, envelopes, clock).claimAndDispatch(10)).isZero();

        verify(mapper).markPrePublishFailure(eq(13L), eq(row.getClaimedAt()), eq("FAILED"), eq(row.getClaimedAt()), contains("outbox envelope"));
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
    }

    private static RobotCommandOutboxDO command(long id) {
        RobotCommandOutboxDO row = new RobotCommandOutboxDO();
        row.setId(id); row.setTenantId(10L); row.setDeviceId(20L); row.setRobotId(30L); row.setMissionId(40L);
        row.setMessageId("01K5K8VJGR9VK8T3H7ZQX3W1AB"); row.setRequestId("request-1");
        row.setTopic("robot/tenant/product/device/command");
        row.setEnvelope("{\"messageId\":\"01K5K8VJGR9VK8T3H7ZQX3W1AB\",\"requestId\":\"request-1\","
                + "\"timestamp\":1757894400000,\"version\":1,\"type\":\"MISSION_START\",\"source\":\"CLOUD\",\"data\":{}}");
        row.setStatus("PENDING"); row.setAttemptCount(0);
        row.setClaimedAt(java.time.LocalDateTime.of(2026, 9, 15, 0, 0));
        return row;
    }
}
