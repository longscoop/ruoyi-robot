package com.robot.platform.robot.mission.message.handler;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.mqtt.RobotProtocolException;
import com.robot.platform.robot.mission.message.MessageHandleResult;
import com.robot.platform.robot.mission.service.MissionService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared Inbox/tenant boundary for MQTT and DEVICE HTTP fallbacks. */
abstract class AbstractMissionMessageHandler<T> {
    private final RobotMessageInboxService inbox;
    protected final MissionService missions;

    protected AbstractMissionMessageHandler(RobotMessageInboxService inbox, MissionService missions) {
        this.inbox = inbox; this.missions = missions;
    }

    protected final MessageHandleResult once(DeviceIdentity device, RobotMessageEnvelope<T> envelope, String expectedType,
                                             MissionMessageOperation<T> operation) {
        if (device == null || envelope == null || envelope.data() == null || envelope.source() != MessageSource.ROBOT
                || envelope.version() != 1 || !expectedType.equals(envelope.type().name())) throw new IllegalArgumentException("invalid robot mission envelope");
        Long previousTenant = TenantContextHolder.getTenantId(); boolean previousIgnore = TenantContextHolder.isIgnore();
        TenantContextHolder.setTenantId(device.tenantId()); TenantContextHolder.setIgnore(false);
        try {
            RobotMessageInboxDO fact = new RobotMessageInboxDO();
            fact.setTenantId(device.tenantId()); fact.setDeviceId(device.deviceId()); fact.setMessageId(envelope.messageId());
            fact.setRequestId(envelope.requestId()); fact.setTopic("device-mission"); fact.setMessageType(expectedType);
            fact.setPayloadHash(sha256(envelope.data().toString())); fact.setResult("PENDING");
            RobotMessageInboxService.InsertResult inserted = inbox.insertOrVerify(fact);
            if (inserted == RobotMessageInboxService.InsertResult.CONFLICT) throw new RobotProtocolException("message id is reused for a different fact");
            if (inserted == RobotMessageInboxService.InsertResult.DUPLICATE) return MessageHandleResult.DUPLICATE;
            MessageHandleResult result = operation.apply(device, envelope);
            // Late terminal reports are still durable audit facts; only their result differs.
            inbox.markResult(fact, result.name());
            return result;
        } finally {
            TenantContextHolder.setTenantId(previousTenant); TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    @FunctionalInterface protected interface MissionMessageOperation<T> {
        MessageHandleResult apply(DeviceIdentity device, RobotMessageEnvelope<T> envelope);
    }
}
