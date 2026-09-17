package com.robot.platform.robot.command.gateway;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.*;
import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;
import com.robot.platform.robot.command.outbox.service.RobotCommandOutboxService;
import com.robot.platform.robot.mission.message.MissionCancelPayload;
import com.robot.platform.robot.mission.message.MissionStartPayload;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/** MQTT adapter is the sole cloud-to-robot transport implementation; Mission code stays transport-neutral. */
@Service
@ConditionalOnMissingBean(RobotCommandGateway.class)
@ConditionalOnBean(RobotCommandOutboxService.class)
@RequiredArgsConstructor
public class MqttRobotCommandGateway implements RobotCommandGateway {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RobotMapper robots;
    private final DeviceMapper devices;
    private final DeviceMqttAuthenticationService identities;
    private final RobotCommandOutboxService outbox;
    private final RobotMessageEnvelopeCodec envelopes;
    private final Clock robotHeartbeatClock;

    @Override
    public void enqueue(RobotCommand command) {
        if (command == null || command.tenantId() <= 0 || command.robotId() <= 0 || command.missionId() <= 0
                || blank(command.requestId()) || blank(command.type())) throw new IllegalArgumentException("invalid robot command");
        DeviceMqttIdentity identity = identity(command);
        RobotMessageEnvelope<?> envelope = envelope(command);
        RobotCommandOutboxDO row = new RobotCommandOutboxDO();
        row.setTenantId(command.tenantId()); row.setDeviceId(identity.deviceId()); row.setRobotId(command.robotId()); row.setMissionId(command.missionId());
        row.setMessageId(envelope.messageId()); row.setRequestId(command.requestId());
        row.setTopic(RobotTopic.of(identity.tenantNamespace(), identity.productKey(), identity.deviceSn(), RobotTopic.Channel.COMMAND).value());
        row.setEnvelope(new String(envelopes.encode(envelope), StandardCharsets.UTF_8)); row.setMessageType(envelope.type().name());
        row.setStatus("PENDING"); row.setAttemptCount(0); row.setNextAttemptTime(java.time.LocalDateTime.ofInstant(robotHeartbeatClock.instant(), java.time.ZoneOffset.UTC));
        outbox.enqueue(row);
    }

    @Override
    public Optional<RobotCommandReceipt> findCommand(String messageId) {
        return outbox.findByMessageId(messageId).map(row -> new RobotCommandReceipt(row.getTenantId(), row.getDeviceId(),
                row.getRobotId(), row.getMissionId(), row.getRequestId(), row.getMessageType(), row.getAttemptCount()));
    }

    @Override
    public StartCancellation invalidateUndeliveredStarts(long tenantId, long missionId) {
        return outbox.invalidateUndeliveredStarts(tenantId, missionId);
    }

    private DeviceMqttIdentity identity(RobotCommand command) {
        RobotDO robot = robots.selectByTenantAndId(command.tenantId(), command.robotId());
        if (robot == null || robot.getDeviceId() == null) throw new IllegalArgumentException("robot is not bound to an active device");
        DeviceDO device = devices.selectById(robot.getDeviceId());
        if (device == null || blank(device.getMqttUsername())) throw new IllegalArgumentException("robot device MQTT identity is unavailable");
        DeviceMqttIdentity identity = identities.findActiveByUsername(device.getMqttUsername())
                .orElseThrow(() -> new IllegalArgumentException("robot device is not MQTT active"));
        if (identity.tenantId() != command.tenantId() || identity.robotId() != command.robotId()
                || identity.deviceId() != robot.getDeviceId()) throw new IllegalArgumentException("robot device identity mismatch");
        return identity;
    }

    private RobotMessageEnvelope<?> envelope(RobotCommand command) {
        MessageType type;
        Object payload;
        if ("MISSION_START".equals(command.type())) {
            type = MessageType.MISSION_START;
            JsonNode missionPayload = JsonUtils.parseObject(command.payload(), JsonNode.class);
            if (missionPayload == null) throw new IllegalArgumentException("mission start payload must be JSON");
            payload = new MissionStartPayload(command.missionId(), missionPayload);
        } else if ("MISSION_CANCEL".equals(command.type())) {
            type = MessageType.MISSION_CANCEL;
            @SuppressWarnings("unchecked") Map<String, Object> value = JsonUtils.parseObject(command.payload(), Map.class);
            payload = new MissionCancelPayload(command.missionId(), value == null ? "" : String.valueOf(value.getOrDefault("reason", "")),
                    value == null ? null : value.get("targetStartMessageId") == null ? null : String.valueOf(value.get("targetStartMessageId")));
        } else throw new IllegalArgumentException("unsupported robot command type");
        return new RobotMessageEnvelope<>(ulid(), command.requestId(), robotHeartbeatClock.millis(), 1, type, MessageSource.CLOUD, payload);
    }

    private static String ulid() {
        long timestamp = System.currentTimeMillis();
        char[] value = new char[26];
        for (int i = 9; i >= 0; i--) { value[i] = CROCKFORD[(int) (timestamp & 31)]; timestamp >>>= 5; }
        for (int i = 10; i < value.length; i++) value[i] = CROCKFORD[RANDOM.nextInt(CROCKFORD.length)];
        return new String(value);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
