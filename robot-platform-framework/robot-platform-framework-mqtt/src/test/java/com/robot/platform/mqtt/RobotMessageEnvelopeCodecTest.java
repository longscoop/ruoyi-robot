package com.robot.platform.mqtt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotMessageEnvelopeCodecTest {
    private static final long NOW = 1_789_041_600_000L;
    private final RobotMessageTypeRegistry registry = new RobotMessageTypeRegistry()
            .register(MessageType.MISSION_START, 1, MissionStart.class);
    private final RobotMessageEnvelopeCodec codec = new RobotMessageEnvelopeCodec(registry, 512,
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    @Test
    void roundTripsOnlyTheRegistryDeclaredPayloadType() {
        RobotMessageEnvelope<MissionStart> source = envelope(new MissionStart("MISSION-1", 3));

        byte[] bytes = codec.encode(source);

        assertThat(codec.decode(bytes, MessageType.MISSION_START)).isEqualTo(source);
    }

    @Test
    void rejectsDataThatClaimsAMessageTypeWithAnotherDtosShape() {
        byte[] forged = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,
                "version":1,"type":"MISSION_START","source":"CLOUD","data":{"temperature":20}}""");

        assertThatThrownBy(() -> codec.decode(forged, MessageType.MISSION_START))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void rejectsUnknownTypeVersionAndPayloadBeforeJsonParsing() {
        byte[] unknownType = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,
                "version":1,"type":"NOT_REGISTERED","source":"CLOUD","data":{}}""");
        byte[] unknownVersion = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,
                "version":2,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}}""");

        assertThatThrownBy(() -> codec.decode(unknownType, null)).isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> codec.decode(unknownVersion, null)).isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> codec.decode(new byte[513], MessageType.MISSION_START))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void rejectsMalformedEnvelopeMetadataAndUnknownJsonFields() {
        byte[] malformed = json("""
                {"messageId":"not-a-ulid","requestId":"bad request id","timestamp":1,"version":1,
                "type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3},"extra":true}""");

        assertThatThrownBy(() -> codec.decode(malformed, MessageType.MISSION_START))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void rejectsDuplicateFieldsTrailingJsonAndNonCanonicalUlids() {
        byte[] duplicate = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,"version":1,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}}""");
        byte[] trailing = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,"version":1,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}} {}""");
        byte[] invalidUlid = json("""
                {"messageId":"81J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,"version":1,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}}""");

        assertThatThrownBy(() -> codec.decode(duplicate, null)).isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> codec.decode(trailing, null)).isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> codec.decode(invalidUlid, null)).isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void rejectsFractionalAndOutOfRangeIntegralMetadata() {
        byte[] fractionalVersion = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,"version":1.5,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}}""");
        byte[] tooLargeVersion = json("""
                {"messageId":"01J0A1B2C3D4E5F6G7H8J9K0MN","requestId":"REQ-1","timestamp":1789041600000,"version":2147483648,"type":"MISSION_START","source":"CLOUD","data":{"missionId":"MISSION-1","priority":3}}""");

        assertThatThrownBy(() -> codec.decode(fractionalVersion, null)).isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> codec.decode(tooLargeVersion, null)).isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void invokesTheDescriptorPayloadValidatorOnDecodeAndEncode() {
        RobotMessageDescriptor<MissionStart> descriptor = new RobotMessageDescriptor<>(MessageType.MISSION_START, 1,
                MissionStart.class, java.util.Set.of(MessageSource.CLOUD), java.util.Set.of(RobotTopic.Channel.COMMAND),
                payload -> { if (payload.priority() < 0) throw new RobotProtocolException("negative priority"); });
        RobotMessageEnvelopeCodec validatingCodec = new RobotMessageEnvelopeCodec(new RobotMessageTypeRegistry().register(descriptor),
                512, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

        assertThatThrownBy(() -> validatingCodec.encode(envelope(new MissionStart("MISSION-1", -1))))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void parsesOnlyCanonicalSingleDeviceTopics() {
        RobotTopic topic = RobotTopic.parse("robot/tenant-a/product-x/SN-1/command");

        assertThat(topic.tenantNamespace()).isEqualTo("tenant-a");
        assertThat(topic.channel()).isEqualTo(RobotTopic.Channel.COMMAND);
        assertThatThrownBy(() -> RobotTopic.parse("robot/tenant-a/product-x/SN-1/command/extra"))
                .isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> RobotTopic.parse("robot/tenant-a/product-x/+/command"))
                .isInstanceOf(RobotProtocolException.class);
        assertThatThrownBy(() -> RobotTopic.parse("robot/tenant%2Da/product-x/SN-1/command"))
                .isInstanceOf(RobotProtocolException.class);
    }

    @Test
    void publisherUsesQosOneAndNeverRetainsAndNeverPretendsDisconnectedIsSuccess() {
        CapturingTransport transport = new CapturingTransport();
        HiveMqRobotMqttClient client = new HiveMqRobotMqttClient(codec, registry, transport);
        RobotTopic topic = RobotTopic.of("tenant-a", "product-x", "SN-1", RobotTopic.Channel.COMMAND);

        assertThatThrownBy(() -> client.publish(topic, envelope(new MissionStart("MISSION-1", 3))).toCompletableFuture().join())
                .hasCauseInstanceOf(RobotProtocolException.class);

        client.connect().toCompletableFuture().join();
        client.publish(topic, envelope(new MissionStart("MISSION-1", 3))).toCompletableFuture().join();

        assertThat(transport.qos).isEqualTo(1);
        assertThat(transport.retain).isFalse();
        assertThat(transport.topic).isEqualTo("robot/tenant-a/product-x/SN-1/command");
    }

    private static RobotMessageEnvelope<MissionStart> envelope(MissionStart data) {
        return new RobotMessageEnvelope<>("01J0A1B2C3D4E5F6G7H8J9K0MN", "REQ-1", NOW, 1,
                MessageType.MISSION_START, MessageSource.CLOUD, data);
    }

    private static byte[] json(String value) {
        return value.replace("\n", "").getBytes(StandardCharsets.UTF_8);
    }

    record MissionStart(String missionId, int priority) { }

    private static final class CapturingTransport implements HiveMqRobotMqttClient.MqttTransport {
        private String topic;
        private int qos;
        private boolean retain;
        private boolean connected;

        @Override public java.util.concurrent.CompletionStage<Void> connect() { connected = true; return CompletableFuture.completedFuture(null); }
        @Override public java.util.concurrent.CompletionStage<Void> disconnect() { connected = false; return CompletableFuture.completedFuture(null); }
        @Override public boolean isConnected() { return connected; }
        @Override public java.util.concurrent.CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) {
            this.topic = topic;
            this.qos = qos;
            this.retain = retain;
            return CompletableFuture.completedFuture(null);
        }
        @Override public java.util.concurrent.CompletionStage<Void> subscribe(String topic, int qos,
                java.util.function.Consumer<HiveMqRobotMqttClient.InboundPublish> callback) { return CompletableFuture.completedFuture(null); }
    }
}
