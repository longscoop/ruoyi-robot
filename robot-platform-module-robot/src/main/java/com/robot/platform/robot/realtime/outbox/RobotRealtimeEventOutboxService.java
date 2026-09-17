package com.robot.platform.robot.realtime.outbox;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.outbox.dal.dataobject.RobotRealtimeEventOutboxDO;
import com.robot.platform.robot.realtime.outbox.dal.mysql.RobotRealtimeEventOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Transactional event store and at-least-once dispatcher; consumers deduplicate by the stable event id. */
@Service
@Slf4j
public class RobotRealtimeEventOutboxService {
    private final RobotRealtimeEventOutboxMapper mapper;
    private final ObjectProvider<WebSocketMessageSender> sender;
    private final Clock clock;
    private final Duration retryDelay;
    private final Duration claimTimeout;

    public RobotRealtimeEventOutboxService(RobotRealtimeEventOutboxMapper mapper,
                                           ObjectProvider<WebSocketMessageSender> sender,
                                           Clock clock,
                                           @Value("${robot.realtime.outbox-retry-delay:PT5S}") Duration retryDelay,
                                           @Value("${robot.realtime.outbox-claim-timeout:PT2M}") Duration claimTimeout) {
        this.mapper = mapper;
        this.sender = sender;
        this.clock = clock;
        this.retryDelay = retryDelay;
        this.claimTimeout = claimTimeout;
        if (retryDelay == null || retryDelay.isNegative() || claimTimeout == null
                || claimTimeout.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("invalid realtime outbox retry or claim duration");
        }
    }

    /** Must be invoked inside the same transaction as the state mutation that produced the event. */
    public long enqueue(TenantRobotRealtimeEvent event) {
        Objects.requireNonNull(event, "event");
        String eventKey = event.robotId() + ":" + event.eventId() + ":" + event.type();
        RobotRealtimeEventOutboxDO row = new RobotRealtimeEventOutboxDO();
        row.setTenantId(event.tenantId());
        row.setRobotId(event.robotId());
        row.setEventKey(eventKey);
        row.setEventType(event.type());
        row.setPayload(JsonUtils.toJsonString(event));
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        row.setNextAttemptTime(now());
        try {
            mapper.insert(row);
            return row.getId();
        } catch (DuplicateKeyException duplicate) {
            Long existing = mapper.selectIdByEventKey(event.tenantId(), eventKey);
            if (existing == null) throw duplicate;
            return existing;
        }
    }

    // afterCommit still has the old transaction's resources bound. Suspend those resources so
    // claim/SENT/RETRY use independent, durable database operations rather than the completed unit.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatchSafely(long id) {
        try {
            dispatch(id);
        } catch (RuntimeException infrastructureFailure) {
            // A committed state transition must never escape through afterCommit. PENDING/RETRY or a
            // stale SENDING claim remains durable and the next scheduled scan tries it again.
            log.warn("[dispatchSafely][Outbox infrastructure unavailable; durable row remains id({})]", id,
                    infrastructureFailure);
        }
    }

    private void dispatch(long id) {
        LocalDateTime now = now();
        if (mapper.claim(id, now, now.minus(claimTimeout)) == 0) return;
        RobotRealtimeEventOutboxDO row = mapper.selectByIdIgnoringTenant(id);
        if (row == null || !now.equals(row.getClaimedAt())) return;
        try {
            WebSocketMessageSender websocket = sender.getIfAvailable();
            if (websocket == null) throw new IllegalStateException("websocket sender unavailable");
            TenantUtils.execute(row.getTenantId(), () -> websocket.send(UserTypeEnum.ADMIN.getValue(),
                    row.getEventType(), row.getPayload()));
            mapper.markSent(id, now);
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            if (message.length() > 500) message = message.substring(0, 500);
            try {
                mapper.markRetry(id, now, now().plus(retryDelay), message);
            } catch (RuntimeException retryPersistenceFailure) {
                failure.addSuppressed(retryPersistenceFailure);
            }
            log.warn("[dispatchSafely][Realtime event delivery failed; queued for retry id({})]", id, failure);
        }
    }

    @Scheduled(fixedDelayString = "${robot.realtime.outbox-scan-interval:PT5S}")
    public void dispatchDue() {
        LocalDateTime now = now();
        for (Long id : mapper.selectDueIds(now, now.minus(claimTimeout), 100)) dispatchSafely(id);
    }

    // Match MySQL datetime(3) exactly: claimed_at is the fence against a timed-out sender.
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
}
