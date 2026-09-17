package com.robot.platform.robot.status.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.mqtt.service.DeviceMqttIdentity;
import com.robot.platform.mqtt.MessageSource;
import com.robot.platform.mqtt.MessageType;
import com.robot.platform.mqtt.RobotMessageEnvelope;
import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxService;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.robot.enums.RobotWorkStatus;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.realtime.model.TenantRobotRealtimeEvent;
import com.robot.platform.robot.realtime.service.RobotRealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Transaction invariant: the MySQL Inbox insert happens before every mutable side effect. Redis
 * failure rolls the transaction back, permitting the device to retry instead of losing telemetry.
 */
@Service
public class RobotHeartbeatServiceImpl implements RobotHeartbeatService {
    private final RobotMapper robots;
    private final RobotMessageInboxService inbox;
    private final RobotLiveStatusStore statuses;
    private final Clock clock;
    private final Duration statusTtl;
    private final Duration snapshotInterval;
    private final RobotRealtimeEventPublisher events;

    @Autowired public RobotHeartbeatServiceImpl(RobotMapper robots, RobotMessageInboxService inbox, RobotLiveStatusStore statuses,
                                     Clock clock, @Qualifier("robotHeartbeatStatusTtl") Duration statusTtl,
                                     @Qualifier("robotHeartbeatSnapshotInterval") Duration snapshotInterval,
                                     RobotRealtimeEventPublisher events) {
        this.robots = robots; this.inbox = inbox; this.statuses = statuses; this.clock = clock;
        if (statusTtl == null || snapshotInterval == null || statusTtl.isZero() || statusTtl.isNegative()
                || snapshotInterval.isNegative()) throw new IllegalArgumentException("invalid heartbeat durations");
        this.statusTtl = statusTtl; this.snapshotInterval = snapshotInterval; this.events = events;
    }
    public RobotHeartbeatServiceImpl(RobotMapper robots, RobotMessageInboxService inbox, RobotLiveStatusStore statuses,
                                     Clock clock, Duration statusTtl, Duration snapshotInterval) {
        this(robots, inbox, statuses, clock, statusTtl, snapshotInterval, event -> { });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcceptResult accept(DeviceMqttIdentity device, RobotMessageEnvelope<HeartbeatPayload> heartbeat) {
        validateAuthenticatedEnvelope(device, heartbeat);
        // MQTT callbacks reuse client threads. Never inherit or leak another device's tenant.
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        TenantContextHolder.setTenantId(device.tenantId());
        TenantContextHolder.setIgnore(false);
        try {
            // The offline scanner takes this same lock before it reads Redis. Holding it first
            // prevents an ONLINE heartbeat from calculating `changed` from a stale OFFLINE/ONLINE
            // snapshot while an offline transaction is committing.
            RobotDO robot = robots.selectByTenantAndIdForUpdate(device.tenantId(), device.robotId());
            if (robot == null || !Long.valueOf(device.deviceId()).equals(robot.getDeviceId())
                    || !Long.valueOf(device.tenantId()).equals(robot.getTenantId())) throw new IllegalArgumentException("device robot identity mismatch");
            if (!inbox.insertIfAbsent(inbox(device, heartbeat))) return AcceptResult.DUPLICATE;
            HeartbeatPayload data = heartbeat.data();
            Instant receivedAt = clock.instant();
            RobotWorkStatus workStatus = RobotWorkStatus.valueOf(data.workStatus());
            RobotLiveStatus live = new RobotLiveStatus(1, RobotOnlineStatus.ONLINE, workStatus, data.battery(), data.cpuUsage(),
                    data.memoryUsage(), data.temperatureCelsius(), data.ipAddress(), data.currentMissionId(), data.softwareVersion(), receivedAt,
                    receivedAt.toEpochMilli());
            statuses.put(device.tenantId(), device.robotId(), live, statusTtl);
            boolean changed = !RobotOnlineStatus.ONLINE.name().equals(robot.getOnlineStatus()) || !same(robot.getWorkStatus(), data.workStatus());
            if (needsSnapshot(robot, data, receivedAt)) updateSnapshot(robot, data, workStatus, receivedAt);
            if (changed) events.publish(new TenantRobotRealtimeEvent(1, device.tenantId(), device.robotId(),
                    heartbeat.requestId(), "ROBOT_STATUS_CHANGED", receivedAt, live, "HEARTBEAT-" + heartbeat.messageId()));
            return AcceptResult.ACCEPTED;
        } finally {
            TenantContextHolder.setTenantId(previousTenant);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private void validateAuthenticatedEnvelope(DeviceMqttIdentity device, RobotMessageEnvelope<HeartbeatPayload> heartbeat) {
        if (device == null || heartbeat == null || heartbeat.data() == null || heartbeat.type() != MessageType.HEARTBEAT
                || heartbeat.source() != MessageSource.ROBOT || heartbeat.version() != 1
                || heartbeat.data().robotId() != device.robotId()
                || Math.abs(clock.millis() - heartbeat.timestamp()) > Duration.ofMinutes(5).toMillis()) {
            throw new IllegalArgumentException("invalid authenticated heartbeat");
        }
    }
    private RobotMessageInboxDO inbox(DeviceMqttIdentity device, RobotMessageEnvelope<HeartbeatPayload> heartbeat) {
        RobotMessageInboxDO fact = new RobotMessageInboxDO();
        fact.setTenantId(device.tenantId()); fact.setDeviceId(device.deviceId()); fact.setMessageId(heartbeat.messageId());
        fact.setRequestId(heartbeat.requestId()); fact.setTopic("robot/" + device.tenantNamespace() + "/" + device.productKey() + "/" + device.deviceSn() + "/state");
        fact.setMessageType(MessageType.HEARTBEAT.name()); fact.setPayloadHash(sha256(heartbeat.data().toString())); fact.setResult("ACCEPTED");
        return fact;
    }
    private boolean needsSnapshot(RobotDO robot, HeartbeatPayload data, Instant now) {
        if (!RobotOnlineStatus.ONLINE.name().equals(robot.getOnlineStatus()) || !same(robot.getWorkStatus(), data.workStatus())
                || !same(robot.getCurrentMissionId(), data.currentMissionId()) || !same(robot.getIpAddress(), data.ipAddress())
                || !same(robot.getSoftwareVersion(), data.softwareVersion())) return true;
        if (robot.getLastHeartbeatTime() == null) return true;
        return !robot.getLastHeartbeatTime().plus(snapshotInterval).isAfter(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }
    private void updateSnapshot(RobotDO robot, HeartbeatPayload data, RobotWorkStatus workStatus, Instant now) {
        int changed = robots.updateHeartbeatSnapshotForLockedRobot(robot.getTenantId(), robot.getId(),
                RobotOnlineStatus.ONLINE.name(), workStatus.name(), data.battery(), data.ipAddress(),
                data.currentMissionId(), data.softwareVersion(), LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (changed != 1) {
            throw new IllegalStateException("locked robot recovery snapshot was not persisted");
        }
    }
    private static boolean same(Object a, Object b) { return java.util.Objects.equals(a, b); }
    private static String sha256(String value) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(hash); } catch (Exception e) { throw new IllegalStateException(e); } }
}
