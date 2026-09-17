package com.robot.platform.mqtt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotMqttAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RobotMqttAutoConfiguration.class));

    @Test
    void disabledMqttCreatesNoNetworkClientButKeepsProtocolExtensionPoint() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RobotMessageTypeRegistry.class);
            assertThat(context).doesNotHaveBean(HiveMqRobotMqttClient.class);
        });
    }

    @Test
    void enabledMqttWithoutCloudCredentialsFailsFast() {
        contextRunner.withPropertyValues("robot.mqtt.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void enabledMqttWithoutAnyDescriptorFailsBeforeCreatingNetworkClient() {
        contextRunner.withPropertyValues("robot.mqtt.enabled=true", "robot.mqtt.host=broker", "robot.mqtt.cloud.client-id=cloud",
                        "robot.mqtt.cloud.username=cloud-user", "robot.mqtt.cloud.password=cloud-password")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("at least one message descriptor"));
    }

    @Test
    void lifecycleAndHealthUseTheTransportConnectionRatherThanOnlyLogicalState() {
        contextRunner.withPropertyValues("robot.mqtt.enabled=true", "robot.mqtt.host=broker", "robot.mqtt.cloud.client-id=cloud",
                        "robot.mqtt.cloud.username=cloud-user", "robot.mqtt.cloud.password=cloud-password")
                .withUserConfiguration(FakeClientConfiguration.class, DescriptorContributorConfiguration.class)
                .run(context -> {
                    RobotMqttLifecycle lifecycle = context.getBean(RobotMqttLifecycle.class);
                    HealthIndicator health = context.getBean("robotMqttHealthIndicator", HealthIndicator.class);
                    lifecycle.start();
                    assertThat(health.health().getStatus()).isEqualTo(Health.up().build().getStatus());
                    context.getBean(FakeTransport.class).connected = false;
                    assertThat(health.health().getStatus()).isEqualTo(Health.down().build().getStatus());
                    lifecycle.stop();
                });
    }

    @Test
    void contextShutdownDisconnectsTransportEvenAfterSubscriptionFailure() {
        FakeTransport[] transport = new FakeTransport[1];
        contextRunner.withPropertyValues("robot.mqtt.enabled=true")
                .withUserConfiguration(FakeClientConfiguration.class, DescriptorContributorConfiguration.class)
                .run(context -> {
                    transport[0] = context.getBean(FakeTransport.class);
                    transport[0].subscribeResult = CompletableFuture.failedFuture(new IllegalStateException("suback rejected"));
                    HiveMqRobotMqttClient client = context.getBean(HiveMqRobotMqttClient.class);
                    assertThatThrownBy(() -> client.subscribe(RobotTopic.of("tenant", "product", "SN", RobotTopic.Channel.STATE))
                            .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
                    assertThat(client.state()).isEqualTo(HiveMqRobotMqttClient.State.FAILED);
                });

        assertThat(transport[0].disconnectCalls).isEqualTo(1);
        assertThat(transport[0].connected).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeClientConfiguration {
        @Bean
        FakeTransport fakeTransport() {
            return new FakeTransport();
        }

        @Bean
        HiveMqRobotMqttClient robotMqttClient(FakeTransport transport, RobotMessageTypeRegistry registry) {
            return new HiveMqRobotMqttClient(new RobotMessageEnvelopeCodec(registry, 1024, Clock.systemUTC()), registry, transport);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DescriptorContributorConfiguration {
        @Bean
        RobotMessageDescriptorContributor descriptorContributor() {
            return registry -> registry.register(RobotMessageDescriptor.of(MessageType.HEARTBEAT, 1, Object.class,
                    java.util.Set.of(MessageSource.ROBOT), java.util.Set.of(RobotTopic.Channel.STATE)));
        }
    }

    static final class FakeTransport implements HiveMqRobotMqttClient.MqttTransport {
        private boolean connected;
        private int disconnectCalls;
        private CompletionStage<Void> subscribeResult = CompletableFuture.completedFuture(null);

        @Override public CompletionStage<Void> connect() { connected = true; return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> disconnect() { disconnectCalls++; connected = false; return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> publish(String topic, byte[] payload, int qos, boolean retain) { return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> subscribe(String topic, int qos, Consumer<HiveMqRobotMqttClient.InboundPublish> callback) { return subscribeResult; }
        @Override public boolean isConnected() { return connected; }
    }
}
