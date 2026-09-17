package com.robot.platform.robot.realtime.service;

import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.outbox.RobotRealtimeEventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persists the event in the caller's transaction, then only uses afterCommit as a dispatch accelerator. */
@Service @RequiredArgsConstructor @Slf4j
public class RobotRealtimeEventPublisherImpl implements RobotRealtimeEventPublisher {
    private final RobotRealtimeEventOutboxService outbox;
    @Override public void publish(TenantRobotRealtimeEvent event) {
        if (event == null || event.tenantId() <= 0) throw new IllegalArgumentException("event tenant is required");
        long outboxId = outbox.enqueue(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch(outboxId); }
            });
            return;
        }
        dispatch(outboxId);
    }

    private void dispatch(long outboxId) {
        try {
            outbox.dispatchSafely(outboxId);
        } catch (RuntimeException transactionBoundaryFailure) {
            // Even failure to suspend/resume resources around the dispatcher cannot undo a commit.
            log.warn("[dispatch][Deferred realtime outbox dispatch id({})]", outboxId, transactionBoundaryFailure);
        }
    }
}
