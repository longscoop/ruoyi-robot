package com.robot.platform.robot.command.outbox.service;

import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.mqtt.RobotMessageEnvelopeCodec;
import com.robot.platform.mqtt.RobotMqttPublisher;
import com.robot.platform.mqtt.RobotProtocolException;
import com.robot.platform.mqtt.RobotTopic;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.command.outbox.dal.mysql.RobotCommandOutboxMapper;
import com.robot.platform.robot.command.gateway.RobotCommandGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reliable outbox dispatcher. Claiming is deliberately isolated from publishing: no database row
 * lock is held while waiting for a broker acknowledgement, and {@code claimedAt} fences stale workers.
 * PUBLISHING authorizes a broker call but is not proof of broker delivery; cancellation's target
 * START tombstone makes any physically reordered MQTT Start safe at the robot.
 */
@Service
@ConditionalOnBean({RobotCommandOutboxMapper.class, RobotMqttPublisher.class})
@Slf4j
public class RobotCommandOutboxService {
    private static final int MAX_ATTEMPTS = 8;
    private final RobotCommandOutboxMapper mapper;
    private final RobotMqttPublisher publisher;
    private final RobotMessageEnvelopeCodec envelopes;
    private final Clock clock;
    private final Duration claimTimeout;
    private final TransactionTemplate transactions;
    private final RobotCommandDeliveryFailureHandler exhausted;

    @Autowired
    public RobotCommandOutboxService(RobotCommandOutboxMapper mapper, RobotMqttPublisher publisher, RobotMessageEnvelopeCodec envelopes, Clock clock,
                                     PlatformTransactionManager transactionManager,
                                     ObjectProvider<RobotCommandDeliveryFailureHandler> exhausted,
                                     @Value("${robot.command.outbox-claim-timeout:PT2M}") Duration claimTimeout) {
        this(mapper, publisher, envelopes, clock, claimTimeout, new TransactionTemplate(transactionManager), exhausted.getIfAvailable());
    }

    /** Focused unit-test constructor: production always supplies a transaction manager. */
    public RobotCommandOutboxService(RobotCommandOutboxMapper mapper, RobotMqttPublisher publisher, RobotMessageEnvelopeCodec envelopes, Clock clock) {
        this(mapper, publisher, envelopes, clock, Duration.ofMinutes(2), null, null);
    }

    /** Focused unit-test constructor for proving that either failure phase reaches the Mission port. */
    public RobotCommandOutboxService(RobotCommandOutboxMapper mapper, RobotMqttPublisher publisher, RobotMessageEnvelopeCodec envelopes,
                                     Clock clock, RobotCommandDeliveryFailureHandler exhausted) {
        this(mapper, publisher, envelopes, clock, Duration.ofMinutes(2), null, exhausted);
    }

    private RobotCommandOutboxService(RobotCommandOutboxMapper mapper, RobotMqttPublisher publisher, RobotMessageEnvelopeCodec envelopes, Clock clock,
                                      Duration claimTimeout, TransactionTemplate transactions, RobotCommandDeliveryFailureHandler exhausted) {
        this.mapper = Objects.requireNonNull(mapper, "mapper"); this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes");
        this.clock = Objects.requireNonNull(clock, "clock"); this.claimTimeout = Objects.requireNonNull(claimTimeout, "claimTimeout");
        this.transactions = transactions;
        this.exhausted = exhausted;
        if (claimTimeout.isZero() || claimTimeout.isNegative()) throw new IllegalArgumentException("claim timeout must be positive");
    }

    /** Claims up to {@code limit} rows and performs broker I/O only after the claim transaction commits. */
    public int claimAndDispatch(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("dispatch limit must be within 1..500");
        List<RobotCommandOutboxDO> claimed = transactions == null ? claim(limit) : transactions.execute(status -> claim(limit));
        if (claimed == null) return 0;
        int sent = 0;
        for (RobotCommandOutboxDO row : claimed) if (deliver(row)) sent++;
        return sent;
    }

    /** Called inside the Mission transaction so persistence of state and its command is atomic. */
    public long enqueue(RobotCommandOutboxDO command) {
        Objects.requireNonNull(command, "command");
        try {
            mapper.insert(command);
            return command.getId();
        } catch (DuplicateKeyException duplicate) {
            RobotCommandOutboxDO existing = mapper.selectByMessageId(command.getMessageId());
            if (existing != null && Objects.equals(existing.getTenantId(), command.getTenantId())
                    && Objects.equals(existing.getMissionId(), command.getMissionId())) return existing.getId();
            throw duplicate;
        }
    }

    /** Read-only lookup used to bind an ACK to an actual command rather than device supplied text. */
    public Optional<RobotCommandOutboxDO> findByMessageId(String messageId) {
        return messageId == null || messageId.isBlank() ? Optional.empty() : Optional.ofNullable(mapper.selectByMessageId(messageId));
    }

    public RobotCommandGateway.StartCancellation invalidateUndeliveredStarts(long tenantId, long missionId) {
        if (tenantId <= 0 || missionId <= 0) throw new IllegalArgumentException("invalid mission identity");
        String startMessageId = mapper.selectStartMessageId(tenantId, missionId);
        int invalidated = mapper.invalidateUndeliveredStarts(tenantId, missionId);
        // If this loses to beginPublishing, the START cannot be recalled. Persist that fact so
        // cancellation's separately durable MISSION_CANCEL is explicitly a compensation command.
        int compensating = mapper.markIrreversibleStartForCancellation(tenantId, missionId);
        if (compensating > 0) return new RobotCommandGateway.StartCancellation(
                RobotCommandGateway.StartCancellationOutcome.COMPENSATING, startMessageId);
        return new RobotCommandGateway.StartCancellation(invalidated > 0
                ? RobotCommandGateway.StartCancellationOutcome.INVALIDATED
                : RobotCommandGateway.StartCancellationOutcome.NO_START_COMMAND, startMessageId);
    }

    private List<RobotCommandOutboxDO> claim(int limit) {
        LocalDateTime now = now();
        List<RobotCommandOutboxDO> rows = mapper.lockDueForDispatch(now, now.minus(claimTimeout), limit);
        for (RobotCommandOutboxDO row : rows) {
            // Every claimed row gets a fresh, millisecond-precise fencing value before returning.
            if (mapper.markSending(row.getId(), now, now, now.minus(claimTimeout)) == 1) {
                row.setClaimedAt(now); row.setAttemptCount((row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1);
            } else {
                row.setId(null);
            }
        }
        return rows.stream().filter(row -> row.getId() != null).toList();
    }

    private boolean deliver(RobotCommandOutboxDO row) {
        boolean publishing = false;
        try {
            RobotTopic topic = RobotTopic.parse(row.getTopic());
            RobotMessageEnvelope<?> envelope = envelopes.decode(row.getEnvelope().getBytes(StandardCharsets.UTF_8), null);
            if (envelope == null || !Objects.equals(row.getMessageId(), envelope.messageId())) {
                throw new RobotProtocolException("outbox envelope does not match durable message id");
            }
            // This UPDATE is the linearization point with cancellation. No DB lock covers broker
            // I/O: cancellation either changes SENDING to FAILED first (no publish), or observes
            // PUBLISHING and records that its MISSION_CANCEL is a required compensation.
            if (mapper.beginPublishing(row.getId(), row.getClaimedAt()) != 1) return false;
            publishing = true;
            publisher.publish(topic, envelope).toCompletableFuture().join();
            return mapper.markSent(row.getId(), row.getClaimedAt()) == 1;
        } catch (RuntimeException failure) {
            int attempt = row.getAttemptCount() == null ? 1 : row.getAttemptCount();
            String status = attempt >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
            String detail = error(failure);
            if ("FAILED".equals(status)) {
                completeExhaustion(row, detail, publishing);
            } else if (publishing) {
                mapper.markRetry(row.getId(), row.getClaimedAt(), status, now().plus(retryDelay(attempt)), detail);
            } else {
                mapper.markPrePublishFailure(row.getId(), row.getClaimedAt(), status, now().plus(retryDelay(attempt)), detail);
            }
            log.warn("[deliver][Robot command delivery failed; messageId({}) status({})]", row.getMessageId(), status, failure);
            return false;
        }
    }

    private void completeExhaustion(RobotCommandOutboxDO row, String detail, boolean publishing) {
        Runnable operation = () -> {
            int failed = publishing
                    ? mapper.markRetry(row.getId(), row.getClaimedAt(), "FAILED", now(), detail)
                    : mapper.markPrePublishFailure(row.getId(), row.getClaimedAt(), "FAILED", now(), detail);
            if (failed == 1 && exhausted != null) {
                exhausted.exhausted(row, "MISSION_COMMAND_DELIVERY_EXHAUSTED", "robot command delivery exhausted: " + detail);
            }
        };
        if (transactions == null) operation.run(); else transactions.executeWithoutResult(status -> operation.run());
    }

    private Duration retryDelay(int attempt) {
        long seconds = 1L << Math.min(Math.max(attempt - 1, 0), 6);
        return Duration.ofSeconds(seconds);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC); }
    private static String error(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
