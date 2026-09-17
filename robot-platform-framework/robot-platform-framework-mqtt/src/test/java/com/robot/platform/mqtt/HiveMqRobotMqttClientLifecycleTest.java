package com.robot.platform.mqtt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class HiveMqRobotMqttClientLifecycleTest {
    private final RobotMessageDescriptor<Command> command = RobotMessageDescriptor.of(MessageType.MISSION_START, 1, Command.class,
            Set.of(MessageSource.CLOUD), Set.of(RobotTopic.Channel.COMMAND));
    private final RobotMessageTypeRegistry registry = new RobotMessageTypeRegistry().register(command);
    private final RobotMessageEnvelopeCodec codec = new RobotMessageEnvelopeCodec(registry, 1024,
            Clock.fixed(Instant.ofEpochMilli(1_789_041_600_000L), ZoneOffset.UTC));

    @Test
    void subscribesWithRealCallbackAndDispatchesOnlyPolicyMatchedTopicAndEnvelope() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        java.util.concurrent.atomic.AtomicReference<RobotInboundMessage<Command>> received = new java.util.concurrent.atomic.AtomicReference<>();
        client.register(command, received::set);
        client.connect().toCompletableFuture().join();

        client.subscribe(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND)).toCompletableFuture().join();
        transport.callback.accept(new HiveMqRobotMqttClient.InboundPublish("robot/tenant-a/product-x/SN-1/command", codec.encode(envelope())));

        assertThat(received.get().topic().channel()).isEqualTo(RobotTopic.Channel.COMMAND);
        assertThat(received.get().envelope().data()).isEqualTo(new Command("M-1"));
        assertThatThrownBy(() -> transport.callback.accept(new HiveMqRobotMqttClient.InboundPublish(
                "robot/tenant-a/product-x/SN-1/state", codec.encode(envelope())))).isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void robotStateDescriptorRejectsCloudCommandSourceAndChannel() {
        RobotMessageDescriptor<Command> state = RobotMessageDescriptor.of(MessageType.MISSION_EVENT, 1, Command.class,
                Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE, RobotTopic.Channel.EVENT));
        RobotMessageTypeRegistry stateRegistry = new RobotMessageTypeRegistry().register(state);
        RobotMessageEnvelopeCodec stateCodec = new RobotMessageEnvelopeCodec(stateRegistry, 1024,
                Clock.fixed(Instant.ofEpochMilli(1_789_041_600_000L), ZoneOffset.UTC));
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(stateCodec, stateRegistry, transport);
        client.register(state, ignored -> { });
        client.connect().toCompletableFuture().join();
        client.subscribe(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.STATE)).toCompletableFuture().join();
        RobotMessageEnvelope<Command> cloudCommand = new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "REQ-1",
                1_789_041_600_000L, 1, MessageType.MISSION_EVENT, MessageSource.CLOUD, new Command("M-1"));

        assertThatThrownBy(() -> transport.callback.accept(new HiveMqRobotMqttClient.InboundPublish(
                "robot/tenant-a/product-x/SN-1/state", stateCodec.encode(cloudCommand))))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void subscribesHeartbeatIngressOnlyThroughTheExactRobotStateWildcard() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        client.connect().toCompletableFuture().join();

        client.subscribeRobotStateWildcard().toCompletableFuture().join();

        assertThat(transport.subscribedTopic).isEqualTo("robot/+/+/+/state");
        assertThat(transport.subscribedQos).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeTransportKeepsOnePublishConsumerAcrossRepeatedSubscriptions() throws Exception {
        var nativeClient = org.mockito.Mockito.mock(com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient.class);
        var consumers = new java.util.ArrayList<Consumer<com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish>>();
        org.mockito.Mockito.doAnswer(call -> { consumers.add(call.getArgument(1)); return null; }).when(nativeClient)
                .publishes(org.mockito.ArgumentMatchers.eq(com.hivemq.client.mqtt.MqttGlobalPublishFilter.SUBSCRIBED),
                        org.mockito.ArgumentMatchers.any(Consumer.class));
        org.mockito.Mockito.when(nativeClient.subscribe(org.mockito.ArgumentMatchers.any(
                com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        org.mockito.Mockito.when(nativeClient.connect()).thenReturn(CompletableFuture.completedFuture(null));
        org.mockito.Mockito.when(nativeClient.disconnect()).thenAnswer(call -> {
            consumers.clear(); // HiveMQ clears all global flows on an explicit disconnect.
            return CompletableFuture.completedFuture(null);
        });
        var constructor = Class.forName(HiveMqRobotMqttClient.class.getName() + "$HiveMqTransport")
                .getDeclaredConstructor(com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient.class);
        constructor.setAccessible(true);
        var transport = (HiveMqRobotMqttClient.MqttTransport) constructor.newInstance(nativeClient);
        var received = new java.util.ArrayList<HiveMqRobotMqttClient.InboundPublish>();

        transport.subscribe("robot/+/+/+/state", 1, received::add).toCompletableFuture().join();
        transport.subscribe("robot/+/+/+/state", 1, received::add).toCompletableFuture().join();
        assertThat(consumers).hasSize(1);
        var publish = com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish.builder()
                .topic("robot/tenant-a/product/SN-1/state").payload(new byte[]{42}).build();
        consumers.forEach(consumer -> consumer.accept(publish));
        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).containsExactly((byte) 42);
        transport.disconnect().toCompletableFuture().join();
        transport.connect().toCompletableFuture().join();
        transport.subscribe("robot/+/+/+/state", 1, received::add).toCompletableFuture().join();
        assertThat(consumers).hasSize(1);
        consumers.forEach(consumer -> consumer.accept(publish));
        assertThat(received).hasSize(2);
    }

    @Test
    void automaticReconnectRestoresTheDesiredHeartbeatWildcardSubscription() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        client.connect().toCompletableFuture().join();
        client.subscribeRobotStateWildcard().toCompletableFuture().join();

        transport.simulateAutomaticReconnect();

        assertThat(transport.subscribeCalls).isEqualTo(2);
        assertThat(transport.subscribedTopic).isEqualTo("robot/+/+/+/state");
    }

    @Test
    void reconnectAfterFailedResubscribeRecoversAndStopSuppressesLateReconnect() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        client.connect().toCompletableFuture().join();
        client.subscribeRobotStateWildcard().toCompletableFuture().join();
        transport.subscribeResult = CompletableFuture.failedFuture(new IllegalStateException("suback rejected"));
        transport.simulateAutomaticReconnect();
        transport.subscribeResult = CompletableFuture.completedFuture(null);

        transport.simulateAutomaticReconnect();

        assertThat(transport.subscribeCalls).isEqualTo(3);
        assertThat(client.isHealthy()).isTrue();
        client.disconnect().toCompletableFuture().join();
        transport.simulateAutomaticReconnect();
        assertThat(transport.subscribeCalls).isEqualTo(3);
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void rejectedHeartbeatWildcardSubscriptionCanBeRetriedOnTheSameConnection() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        client.connect().toCompletableFuture().join();
        transport.subscribeResult = CompletableFuture.failedFuture(new IllegalStateException("suback rejected"));

        assertThatThrownBy(() -> client.subscribeRobotStateWildcard().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(client.isHealthy()).isTrue();

        transport.subscribeResult = CompletableFuture.completedFuture(null);
        client.subscribeRobotStateWildcard().toCompletableFuture().join();
        assertThat(transport.subscribeCalls).isEqualTo(2);
        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void rejectsConnectSubscribeAndPublishFailuresWithoutClaimingHealthyState() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        transport.connectResult = CompletableFuture.failedFuture(new IllegalStateException("broker down"));

        assertThatThrownBy(() -> client.connect().toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(client.isHealthy()).isFalse();
        transport.connectResult = CompletableFuture.completedFuture(null);
        client.connect().toCompletableFuture().join();
        transport.publishResult = CompletableFuture.failedFuture(new IllegalStateException("no puback"));
        assertThatThrownBy(() -> client.publish(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND), envelope())
                .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void refusesDuplicateHandlerRegistrationAndSubscribeFailure() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        client.register(command, ignored -> { });
        assertThatThrownBy(() -> client.register(command, ignored -> { }))
                .isInstanceOf(RobotProtocolException.class);
        client.connect().toCompletableFuture().join();
        transport.subscribeResult = CompletableFuture.failedFuture(new IllegalStateException("suback rejected"));

        assertThatThrownBy(() -> client.subscribe(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND))
                .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void dispatchesSameMessageTypeByRegistryDeclaredVersionAndRejectsOnlyDuplicateVersion() {
        RobotMessageDescriptor<Command> v1 = RobotMessageDescriptor.of(MessageType.MISSION_START, 1, Command.class,
                Set.of(MessageSource.CLOUD), Set.of(RobotTopic.Channel.COMMAND));
        RobotMessageDescriptor<Command> v2 = RobotMessageDescriptor.of(MessageType.MISSION_START, 2, Command.class,
                Set.of(MessageSource.CLOUD), Set.of(RobotTopic.Channel.COMMAND));
        RobotMessageTypeRegistry versions = new RobotMessageTypeRegistry().register(v1).register(v2);
        RobotMessageEnvelopeCodec versionCodec = new RobotMessageEnvelopeCodec(versions, 1024,
                Clock.fixed(Instant.ofEpochMilli(1_789_041_600_000L), ZoneOffset.UTC));
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(versionCodec, versions, transport);
        java.util.concurrent.atomic.AtomicInteger v1Calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger v2Calls = new java.util.concurrent.atomic.AtomicInteger();
        client.register(v1, ignored -> v1Calls.incrementAndGet());
        client.register(v2, ignored -> v2Calls.incrementAndGet());
        assertThatThrownBy(() -> client.register(v1, ignored -> { })).isInstanceOf(RobotProtocolException.class);
        client.connect().toCompletableFuture().join();
        client.subscribe(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND)).toCompletableFuture().join();

        transport.callback.accept(new HiveMqRobotMqttClient.InboundPublish("robot/tenant-a/product-x/SN-1/command",
                versionCodec.encode(new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "REQ-1", 1_789_041_600_000L,
                        1, MessageType.MISSION_START, MessageSource.CLOUD, new Command("M-1")))));
        transport.callback.accept(new HiveMqRobotMqttClient.InboundPublish("robot/tenant-a/product-x/SN-1/command",
                versionCodec.encode(new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MP", "REQ-2", 1_789_041_600_000L,
                        2, MessageType.MISSION_START, MessageSource.CLOUD, new Command("M-2")))));

        assertThat(v1Calls).hasValue(1);
        assertThat(v2Calls).hasValue(1);
    }

    @Test
    void transportDisconnectMakesHealthDownEvenBeforeAnyOperationCompletes() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        RobotMqttLifecycle lifecycle = new RobotMqttLifecycle(client);
        lifecycle.start();
        transport.connected = false;

        assertThat(client.state()).isEqualTo(HiveMqRobotMqttClient.State.CONNECTED);
        assertThat(client.isHealthy()).isFalse();
        assertThatThrownBy(() -> client.publish(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND), envelope())
                .toCompletableFuture().join()).hasCauseInstanceOf(RobotProtocolException.class);
    }

    @Test
    void disconnectStillClosesTransportAfterPublishFailureAndLifecycleCanStopIt() {
        FakeTransport transport = new FakeTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        RobotMqttLifecycle lifecycle = new RobotMqttLifecycle(client);
        lifecycle.start();
        transport.publishResult = CompletableFuture.failedFuture(new IllegalStateException("puback rejected"));
        assertThatThrownBy(() -> client.publish(RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND), envelope())
                .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);

        lifecycle.stop();

        assertThat(transport.disconnectCalls).isEqualTo(1);
        assertThat(client.state()).isEqualTo(HiveMqRobotMqttClient.State.DISCONNECTED);
    }

    @Test
    void concurrentStopWaitsForPendingConnectThenDisconnectsExactlyOnce() throws Exception {
        ControllableConnectTransport transport = new ControllableConnectTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        RobotMqttLifecycle lifecycle = new RobotMqttLifecycle(client);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> start = CompletableFuture.runAsync(lifecycle::start, executor);
            assertThat(transport.connectCalled.await(1, TimeUnit.SECONDS)).isTrue();
            CountDownLatch stopAttempted = new CountDownLatch(1);
            AtomicReference<Thread> stopThread = new AtomicReference<>();
            CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
                stopThread.set(Thread.currentThread());
                stopAttempted.countDown();
                lifecycle.stop();
            }, executor);

            assertThat(stopAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            assertBlockedOnLifecycleMonitor(stop, stopThread.get());
            transport.connectResult.complete(null);
            start.get(1, TimeUnit.SECONDS);
            Throwable stopFailure = catchThrowable(() -> stop.get(1, TimeUnit.SECONDS));

            assertThat(stopFailure).isNull();
            assertThat(transport.disconnectCalls).hasValue(1);
            assertThat(lifecycle.isRunning()).isFalse();
            assertThat(client.state()).isEqualTo(HiveMqRobotMqttClient.State.DISCONNECTED);
            assertThat(client.isHealthy()).isFalse();
        } finally {
            transport.connectResult.completeExceptionally(new IllegalStateException("test cleanup"));
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentStopDoesNotDeadlockWhenPendingConnectFails() throws Exception {
        ControllableConnectTransport transport = new ControllableConnectTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        RobotMqttLifecycle lifecycle = new RobotMqttLifecycle(client);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> start = CompletableFuture.runAsync(lifecycle::start, executor);
            assertThat(transport.connectCalled.await(1, TimeUnit.SECONDS)).isTrue();
            CountDownLatch stopAttempted = new CountDownLatch(1);
            AtomicReference<Thread> stopThread = new AtomicReference<>();
            CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
                stopThread.set(Thread.currentThread());
                stopAttempted.countDown();
                lifecycle.stop();
            }, executor);

            assertThat(stopAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            assertBlockedOnLifecycleMonitor(stop, stopThread.get());
            transport.connectResult.completeExceptionally(new IllegalStateException("broker down"));
            Throwable startFailure = catchThrowable(() -> start.get(1, TimeUnit.SECONDS));
            Throwable stopFailure = catchThrowable(() -> stop.get(1, TimeUnit.SECONDS));

            assertThat(startFailure).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(stopFailure).isNull();
            assertThat(transport.disconnectCalls).hasValue(0);
            assertThat(lifecycle.isRunning()).isFalse();
            assertThat(client.state()).isEqualTo(HiveMqRobotMqttClient.State.FAILED);
            assertThat(client.isHealthy()).isFalse();
        } finally {
            transport.connectResult.completeExceptionally(new IllegalStateException("test cleanup"));
            executor.shutdownNow();
        }
    }

    @Test
    void convenienceRegistryRegistrationIsOutboundOnlyAndCannotRegisterInboundHandler() {
        RobotMessageTypeRegistry outboundOnly = new RobotMessageTypeRegistry().register(MessageType.MISSION_ACK, 1, Command.class);
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(
                new RobotMessageEnvelopeCodec(outboundOnly, 1024, Clock.fixed(Instant.ofEpochMilli(1_789_041_600_000L), ZoneOffset.UTC)),
                outboundOnly, new FakeTransport());

        assertThat(outboundOnly.isEmpty()).isFalse();
        assertThatThrownBy(() -> client.register(MessageType.MISSION_ACK, Command.class, ignored -> { }))
                .isInstanceOf(RobotProtocolException.class);
    }

    private static RobotMessageEnvelope<Command> envelope() {
        return new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "REQ-1", 1_789_041_600_000L, 1,
                MessageType.MISSION_START, MessageSource.CLOUD, new Command("M-1"));
    }
    private static void assertBlockedOnLifecycleMonitor(CompletableFuture<Void> stop, Thread stopThread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!stop.isDone() && stopThread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(stop.isDone()).isFalse();
        assertThat(stopThread.getState()).isEqualTo(Thread.State.BLOCKED);
    }
    record Command(String missionId) { }
    private static final class FakeTransport implements HiveMqRobotMqttClient.MqttTransport {
        boolean connected;
        CompletionStage<Void> connectResult = CompletableFuture.completedFuture(null);
        CompletionStage<Void> publishResult = CompletableFuture.completedFuture(null);
        CompletionStage<Void> subscribeResult = CompletableFuture.completedFuture(null);
        int disconnectCalls;
        int subscribeCalls;
        String subscribedTopic;
        int subscribedQos;
        Consumer<HiveMqRobotMqttClient.InboundPublish> callback;
        Runnable connectedListener;
        @Override public CompletionStage<Void> connect() { connected = true; return connectResult; }
        @Override public CompletionStage<Void> disconnect() { disconnectCalls++; connected = false; return CompletableFuture.completedFuture(null); }
        @Override public boolean isConnected() { return connected; }
        @Override public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) { return publishResult; }
        @Override public CompletionStage<Void> subscribe(String topic, int qos, Consumer<HiveMqRobotMqttClient.InboundPublish> callback) {
            subscribeCalls++;
            subscribedTopic = topic;
            subscribedQos = qos;
            this.callback = callback;
            return subscribeResult;
        }
        @Override public void setConnectedListener(Runnable listener) { this.connectedListener = listener; }
        void simulateAutomaticReconnect() {
            connected = false;
            connected = true;
            if (connectedListener != null) connectedListener.run();
        }
    }

    private static final class ControllableConnectTransport implements HiveMqRobotMqttClient.MqttTransport {
        private final CountDownLatch connectCalled = new CountDownLatch(1);
        private final CompletableFuture<Void> connectResult = new CompletableFuture<>();
        private final java.util.concurrent.atomic.AtomicInteger disconnectCalls = new java.util.concurrent.atomic.AtomicInteger();
        private volatile boolean connected;

        @Override public CompletionStage<Void> connect() {
            connectCalled.countDown();
            return connectResult.whenComplete((ignored, error) -> connected = error == null);
        }
        @Override public CompletionStage<Void> disconnect() {
            disconnectCalls.incrementAndGet();
            connected = false;
            return CompletableFuture.completedFuture(null);
        }
        @Override public boolean isConnected() { return connected; }
        @Override public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> subscribe(String topic, int qos,
                                                         Consumer<HiveMqRobotMqttClient.InboundPublish> callback) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
