package com.robot.platform.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * MQTT 5 boundary with a thread-safe lifecycle. Invariants: successful stages mean broker
 * acknowledgement, all outbound messages use QoS 1/non-retained delivery, and inbound dispatch
 * accepts only a registry-owned descriptor matching both exact topic channel and message source.
 */
public final class HiveMqRobotMqttClient implements RobotMqttPublisher, RobotMqttSubscriber {
    public enum State { NEW, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, FAILED }

    private final RobotMessageEnvelopeCodec codec;
    private final RobotMessageTypeRegistry registry;
    private final MqttTransport transport;
    private final Map<HandlerKey, RegisteredHandler<?>> handlers = new ConcurrentHashMap<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private volatile boolean robotStateWildcardDesired;
    private volatile boolean explicitlyStopped;

    public HiveMqRobotMqttClient(RobotMessageEnvelopeCodec codec, RobotMessageTypeRegistry registry,
                                 MqttTransport transport) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.transport.setConnectedListener(this::restoreDesiredSubscriptions);
    }

    /** Cloud credentials are independent from EMQX callback credentials and are required eagerly. */
    public static HiveMqRobotMqttClient forBroker(RobotMessageEnvelopeCodec codec, RobotMessageTypeRegistry registry,
                                                   String host, int port, String clientId, String username, String password) {
        if (blank(host) || port < 1 || port > 65_535 || blank(clientId) || blank(username) || blank(password)) {
            throw new IllegalArgumentException("MQTT broker and cloud identity configuration are required");
        }
        AtomicReference<HiveMqTransport> transportReference = new AtomicReference<>();
        Mqtt5AsyncClient client = MqttClient.builder().useMqttVersion5().identifier(clientId).serverHost(host)
                .serverPort(port).simpleAuth().username(username).password(password.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth().automaticReconnectWithDefaultConfig()
                .addConnectedListener(ignored -> {
                    HiveMqTransport connectedTransport = transportReference.get();
                    if (connectedTransport != null) connectedTransport.notifyConnected();
                }).buildAsync();
        HiveMqTransport transport = new HiveMqTransport(client);
        transportReference.set(transport);
        return new HiveMqRobotMqttClient(codec, registry, transport);
    }

    public synchronized CompletionStage<Void> connect() {
        if (!transitionToConnecting()) return failed("MQTT client cannot connect in " + state.get());
        explicitlyStopped = false;
        return transport.connect().whenComplete((ignored, error) ->
                state.compareAndSet(State.CONNECTING, error == null ? State.CONNECTED : State.FAILED));
    }

    public synchronized CompletionStage<Void> disconnect() {
        if (!transitionToDisconnecting()) return failed("MQTT client is not connected");
        explicitlyStopped = true;
        return transport.disconnect().whenComplete((ignored, error) ->
                state.compareAndSet(State.DISCONNECTING, error == null ? State.DISCONNECTED : State.FAILED));
    }

    @Override
    public CompletionStage<Void> publish(RobotTopic topic, RobotMessageEnvelope<?> envelope) {
        if (!isHealthy()) return failed("MQTT client is not connected");
        return transport.publish(topic.value(), codec.encode(envelope), 1, false)
                .whenComplete((ignored, error) -> failConnectedState(error));
    }

    public CompletionStage<Void> subscribe(RobotTopic topic) {
        if (!isHealthy()) return failed("MQTT client is not connected");
        return transport.subscribe(topic.value(), 1, this::onPublish)
                .whenComplete((ignored, error) -> failConnectedState(error));
    }
    @Override public CompletionStage<Void> subscribeRobotStateWildcard() {
        if (!isHealthy()) return failed("MQTT client is not connected");
        return transport.subscribe("robot/+/+/+/state", 1, this::onPublish)
                .thenRun(() -> robotStateWildcardDesired = true);
    }

    public State state() { return state.get(); }

    /** Health always incorporates the live transport state; a stale logical CONNECTED flag is insufficient. */
    public boolean isHealthy() { return state.get() == State.CONNECTED && transport.isConnected(); }

    @Override
    @SuppressWarnings("unchecked") // payload type is compared to the registry descriptor before adapting the legacy v1 API.
    public <T> void register(MessageType type, Class<T> payloadType, RobotMessageHandler<T> handler) {
        RobotMessageDescriptor<?> descriptor = registry.requiredDescriptor(type, 1);
        if (handler == null || descriptor.payloadType() != payloadType) {
            throw new RobotProtocolException("handler payload type is not registry declared");
        }
        register((RobotMessageDescriptor<T>) descriptor, inbound -> handler.handle(inbound.envelope()));
    }

    @Override
    public <T> void register(RobotMessageDescriptor<T> descriptor, RobotInboundMessageHandler<T> handler) {
        if (handler == null || !descriptor.supportsInbound()
                || registry.requiredDescriptor(descriptor.type(), descriptor.version()) != descriptor) {
            throw new RobotProtocolException("handler descriptor is not registry declared");
        }
        HandlerKey key = new HandlerKey(descriptor.type(), descriptor.version());
        if (handlers.putIfAbsent(key, new RegisteredHandler<>(descriptor, handler)) != null) {
            throw new RobotProtocolException("handler already registered for message type/version");
        }
    }

    public void onPublish(InboundPublish publish) {
        RobotTopic topic = RobotTopic.parse(publish.topic());
        RobotMessageEnvelope<?> envelope = codec.decode(publish.payload(), null);
        RegisteredHandler<?> handler = handlers.get(new HandlerKey(envelope.type(), envelope.version()));
        if (handler == null) throw new RobotProtocolException("no handler registered for message type/version");
        handler.handle(topic, envelope);
    }

    private boolean transitionToConnecting() {
        for (;;) {
            State current = state.get();
            if (current != State.NEW && current != State.DISCONNECTED && current != State.FAILED) return false;
            if (state.compareAndSet(current, State.CONNECTING)) return true;
        }
    }

    private void failConnectedState(Throwable error) {
        if (error != null) state.compareAndSet(State.CONNECTED, State.FAILED);
    }

    private synchronized void restoreDesiredSubscriptions() {
        if (explicitlyStopped || !robotStateWildcardDesired || !transport.isConnected()) return;
        // A failed SUBACK/publish must not poison every later automatic reconnect. A late
        // connected notification after an explicit stop still cannot revive the lifecycle.
        state.compareAndSet(State.FAILED, State.CONNECTED);
        if (state.get() != State.CONNECTED) return;
        transport.subscribe("robot/+/+/+/state", 1, this::onPublish)
                .whenComplete((ignored, error) -> failConnectedState(error));
    }

    /** A failed publish/subscribe may leave a socket to clean up, so FAILED can enter disconnecting too. */
    private boolean transitionToDisconnecting() {
        for (;;) {
            State current = state.get();
            if (current != State.CONNECTED && current != State.FAILED) return false;
            if (state.compareAndSet(current, State.DISCONNECTING)) return true;
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static CompletionStage<Void> failed(String message) {
        return CompletableFuture.failedFuture(new RobotProtocolException(message));
    }

    public record InboundPublish(String topic, byte[] payload) { }
    private record HandlerKey(MessageType type, int version) { }

    public interface MqttTransport {
        CompletionStage<Void> connect();
        CompletionStage<Void> disconnect();
        CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain);
        CompletionStage<Void> subscribe(String topic, int qos, Consumer<InboundPublish> callback);

        /** Invoked after the transport reconnects automatically, including a broker session reset. */
        default void setConnectedListener(Runnable listener) { }

        /** True only while the underlying client has an active broker connection. */
        boolean isConnected();
    }

    private static final class HiveMqTransport implements MqttTransport {
        private final Mqtt5AsyncClient client;
        private volatile Runnable connectedListener = () -> { };
        private volatile Consumer<InboundPublish> inbound = ignored -> { };
        private boolean consumerRegistered;
        private HiveMqTransport(Mqtt5AsyncClient client) {
            this.client = client;
            registerConsumer();
        }
        private synchronized void registerConsumer() {
            if (consumerRegistered) return;
            // Per-subscription callbacks accumulate on repeated SUBSCRIBE. One global consumer
            // survives reconnects while broker subscriptions can be safely restored repeatedly.
            client.publishes(MqttGlobalPublishFilter.SUBSCRIBED, publish -> inbound.accept(
                    new InboundPublish(publish.getTopic().toString(), publish.getPayloadAsBytes())));
            consumerRegistered = true;
        }
        @Override public void setConnectedListener(Runnable listener) {
            connectedListener = Objects.requireNonNull(listener, "listener");
        }
        private void notifyConnected() { connectedListener.run(); }
        @Override public CompletionStage<Void> connect() {
            registerConsumer();
            return client.connect().thenApply(ignored -> null);
        }
        @Override public CompletionStage<Void> disconnect() {
            return client.disconnect().thenRun(() -> { synchronized (this) { consumerRegistered = false; } });
        }
        @Override public boolean isConnected() { return client.getConfig().getState().isConnected(); }
        @Override public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
            if (qos != 1 || retain) return failed("invalid publish contract");
            return client.publishWith().topic(topic).qos(MqttQos.AT_LEAST_ONCE).retain(false).payload(payload).send()
                    .thenApply(ignored -> null);
        }
        @Override public CompletionStage<Void> subscribe(String topic, int qos, Consumer<InboundPublish> callback) {
            if (qos != 1) return failed("invalid subscribe contract");
            inbound = Objects.requireNonNull(callback, "callback");
            return client.subscribe(Mqtt5Subscribe.builder().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).build())
                    .thenApply(ignored -> null);
        }
    }

    private record RegisteredHandler<T>(RobotMessageDescriptor<T> descriptor, RobotInboundMessageHandler<T> delegate) {
        @SuppressWarnings("unchecked")
        void handle(RobotTopic topic, RobotMessageEnvelope<?> envelope) {
            if (!descriptor.allowedSources().contains(envelope.source()) || !descriptor.allowedChannels().contains(topic.channel())
                    || !descriptor.payloadType().isInstance(envelope.data())) {
                throw new RobotProtocolException("inbound topic/envelope policy violation");
            }
            delegate.handle(new RobotInboundMessage<>(topic, (RobotMessageEnvelope<T>) envelope));
        }
    }
}
