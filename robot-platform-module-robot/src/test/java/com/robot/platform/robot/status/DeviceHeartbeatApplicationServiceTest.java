package com.robot.platform.robot.status;

import com.robot.platform.mqtt.*;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.service.DeviceHeartbeatApplicationService;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceHeartbeatApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private final RobotMapper robots = mock(RobotMapper.class);
    private final RobotHeartbeatService heartbeats = mock(RobotHeartbeatService.class);
    private final RobotMessageEnvelopeCodec codec = codec();
    private final DeviceHeartbeatApplicationService service = new DeviceHeartbeatApplicationService(robots, heartbeats, codec);

    @Test
    void httpAndMqttUseTheSameStrictEnvelopeDecoderAndRejectUnknownPayloadFields() {
        when(robots.selectByTenantAndDeviceId(10, 4)).thenReturn(robot());
        byte[] payload = ("{\"messageId\":\"01K4C3W8000000000000000000\",\"requestId\":\"REQ-1\","
                + "\"timestamp\":" + NOW.toEpochMilli() + ",\"version\":1,\"type\":\"HEARTBEAT\",\"source\":\"ROBOT\","
                + "\"data\":{\"robotId\":7,\"battery\":70,\"cpuUsage\":20,\"memoryUsage\":30,"
                + "\"temperatureCelsius\":25,\"workStatus\":\"IDLE\",\"ipAddress\":\"127.0.0.1\","
                + "\"currentMissionId\":null,\"softwareVersion\":\"1.0\",\"unexpected\":true}}")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.accept(10, 4, payload)).isInstanceOf(RobotProtocolException.class);

        verifyNoInteractions(heartbeats);
    }

    @Test
    void resolvesTheBoundRobotInsideTheApplicationService() {
        when(robots.selectByTenantAndDeviceId(10, 4)).thenReturn(robot());
        byte[] payload = codec.encode(new RobotMessageEnvelope<>("01K4C3W8000000000000000000", "REQ-1",
                NOW.toEpochMilli(), 1, MessageType.HEARTBEAT, MessageSource.ROBOT,
                new HeartbeatPayload(7, 70, 20, 30, 25, "IDLE", "127.0.0.1", null, "1.0")));

        service.accept(10, 4, payload);

        verify(heartbeats).accept(argThat(identity -> identity.tenantId() == 10 && identity.deviceId() == 4
                && identity.robotId() == 7), any());
    }

    private static RobotMessageEnvelopeCodec codec() {
        RobotMessageTypeRegistry registry = new RobotMessageTypeRegistry();
        registry.register(new RobotMessageDescriptor<>(MessageType.HEARTBEAT, 1, HeartbeatPayload.class,
                Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE), ignored -> { }));
        return new RobotMessageEnvelopeCodec(registry, 64 * 1024, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RobotDO robot() {
        RobotDO robot = new RobotDO(); robot.setId(7L); robot.setTenantId(10L); robot.setDeviceId(4L);
        return robot;
    }
}
