package com.robot.platform.robot.status.mqtt;

import com.robot.platform.device.mqtt.service.DeviceMqttAuthenticationService;
import com.robot.platform.mqtt.*;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import com.robot.platform.robot.status.service.RobotHeartbeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RobotHeartbeatMqttAdapterTest {
    private final RobotMqttSubscriber subscriber = mock(RobotMqttSubscriber.class);
    private final RobotMessageTypeRegistry registry = registry();
    private final DeviceMqttAuthenticationService identities = mock(DeviceMqttAuthenticationService.class);
    private final RobotHeartbeatService heartbeats = mock(RobotHeartbeatService.class);

    @Test
    void productionLifecycleRegistersHandlerThenSubscribesStateWildcard() {
        when(subscriber.subscribeRobotStateWildcard()).thenReturn(CompletableFuture.completedFuture(null));
        RobotHeartbeatMqttAdapter adapter = adapter();

        adapter.start();

        verify(subscriber).register(any(RobotMessageDescriptor.class), any(RobotInboundMessageHandler.class));
        verify(subscriber).subscribeRobotStateWildcard();
        assertThat(adapter.isRunning()).isTrue();
    }

    @Test
    void initialSubscribeFailureCanBeRetriedWithoutRegisteringTheHandlerTwice() {
        when(subscriber.subscribeRobotStateWildcard()).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("suback rejected")),
                CompletableFuture.completedFuture(null));
        RobotHeartbeatMqttAdapter adapter = adapter();

        assertThatThrownBy(adapter::start).hasCauseInstanceOf(IllegalStateException.class);
        adapter.start();

        verify(subscriber, times(1)).register(any(RobotMessageDescriptor.class), any(RobotInboundMessageHandler.class));
        verify(subscriber, times(2)).subscribeRobotStateWildcard();
        assertThat(adapter.isRunning()).isTrue();
    }

    @Test
    void rejectsInboundTopicWithoutAnActiveMatchingDeviceIdentity() {
        when(identities.findActiveByUsername("tenant-a/product/SN-1")).thenReturn(Optional.empty());
        RobotHeartbeatMqttAdapter adapter = adapter();
        HeartbeatPayload data = new HeartbeatPayload(7, 70, 20, 30, 25, "IDLE", "127.0.0.1", null, "1.0");
        RobotMessageEnvelope<HeartbeatPayload> envelope = new RobotMessageEnvelope<>(
                "01K4C3W8000000000000000000", "REQ-1", 1_789_041_600_000L, 1,
                MessageType.HEARTBEAT, MessageSource.ROBOT, data);

        assertThatThrownBy(() -> adapter.handle(new RobotInboundMessage<>(
                RobotTopic.parse("robot/tenant-a/product/SN-1/state"), envelope)))
                .isInstanceOf(RobotProtocolException.class);

        verifyNoInteractions(heartbeats);
    }

    private RobotHeartbeatMqttAdapter adapter() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(); beans.addBean("subscriber", subscriber);
        return new RobotHeartbeatMqttAdapter(beans.getBeanProvider(RobotMqttSubscriber.class), registry, identities, heartbeats);
    }

    private static RobotMessageTypeRegistry registry() {
        RobotMessageTypeRegistry registry = new RobotMessageTypeRegistry();
        registry.register(new RobotMessageDescriptor<>(MessageType.HEARTBEAT, 1, HeartbeatPayload.class,
                Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE), ignored -> { }));
        return registry;
    }
}
