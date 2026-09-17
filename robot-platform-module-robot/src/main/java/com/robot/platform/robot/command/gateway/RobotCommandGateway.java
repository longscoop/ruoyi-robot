package com.robot.platform.robot.command.gateway;

import java.util.Optional;

/** Single cloud-to-robot behavior boundary. Task 11 supplies the durable MQTT/Outbox adapter. */
public interface RobotCommandGateway {
    void enqueue(RobotCommand command);

    /**
     * Resolves a robot acknowledgement back to the command we durably created.  Inbound payloads
     * cannot be trusted to say whether they acknowledge a start or a cancellation command.
     */
    default Optional<RobotCommandReceipt> findCommand(String messageId) {
        return Optional.empty();
    }

    /**
     * Stops START deliveries which have not crossed the irreversible broker-publish boundary.
     * A compensating cancel command is required when this reports {@link StartCancellation#COMPENSATING}.
     */
    default StartCancellation invalidateUndeliveredStarts(long tenantId, long missionId) {
        return new StartCancellation(StartCancellationOutcome.NO_START_COMMAND, null);
    }

    enum StartCancellationOutcome {
        /** Cancellation won the atomic transition, so the START will never be published. */
        INVALIDATED,
        /** START was already in the broker-publish phase; enqueue MISSION_CANCEL as compensation. */
        COMPENSATING,
        /** There was no durable START command for this mission. */
        NO_START_COMMAND
    }

    /**
     * The target START id is a protocol tombstone. A robot receiving MISSION_CANCEL first must
     * remember this id and reject a later MISSION_START carrying the same envelope message id.
     * This prevents robot execution even though broker I/O is intentionally outside DB locks and
     * a physical MQTT publish may be reordered with cancellation.
     */
    record StartCancellation(StartCancellationOutcome outcome, String startMessageId) { }

    record RobotCommandReceipt(long tenantId, long deviceId, long robotId, long missionId,
                               String requestId, String messageType, int deliveryAttempt) { }
}
