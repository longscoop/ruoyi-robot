package com.robot.platform.robot.realtime;

import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.outbox.RobotRealtimeEventOutboxService;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisherImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import static org.mockito.Mockito.*;

class RobotRealtimeEventPublisherTest {
    @AfterEach void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void publisherPersistsBeforeDispatchOutsideATransaction() {
        RobotRealtimeEventOutboxService outbox = mock(RobotRealtimeEventOutboxService.class);
        when(outbox.enqueue(event())).thenReturn(41L);
        RobotRealtimeEventPublisher publisher = new RobotRealtimeEventPublisherImpl(outbox);

        publisher.publish(event());

        var order = inOrder(outbox);
        order.verify(outbox).enqueue(event());
        order.verify(outbox).dispatchSafely(41L);
    }

    @Test void publisherPersistsInTransactionAndDispatchesOnlyAfterCommit() {
        RobotRealtimeEventOutboxService outbox = mock(RobotRealtimeEventOutboxService.class);
        when(outbox.enqueue(event())).thenReturn(42L);
        RobotRealtimeEventPublisher publisher = new RobotRealtimeEventPublisherImpl(outbox);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(event());
        verify(outbox).enqueue(event());
        verify(outbox, never()).dispatchSafely(anyLong());
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();

        verify(outbox).dispatchSafely(42L);
    }

    @Test void dispatcherTransactionBoundaryFailureCannotEscapeAfterCommit() {
        RobotRealtimeEventOutboxService outbox = mock(RobotRealtimeEventOutboxService.class);
        when(outbox.enqueue(event())).thenReturn(43L);
        doThrow(new IllegalStateException("transaction resources unavailable")).when(outbox).dispatchSafely(43L);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        new RobotRealtimeEventPublisherImpl(outbox).publish(event());

        org.assertj.core.api.Assertions.assertThatCode(() ->
                TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit()).doesNotThrowAnyException();
    }

    private static TenantRobotRealtimeEvent event() {
        Instant at = Instant.parse("2026-09-13T12:00:00Z");
        RobotLiveStatus status = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, RobotWorkStatus.IDLE,
                72, 30, 40, 25, "127.0.0.1", null, "1.0", at, at.toEpochMilli());
        return new TenantRobotRealtimeEvent(1, 20, 7, "REQ-1", "ROBOT_STATUS_CHANGED", at, status);
    }
}
