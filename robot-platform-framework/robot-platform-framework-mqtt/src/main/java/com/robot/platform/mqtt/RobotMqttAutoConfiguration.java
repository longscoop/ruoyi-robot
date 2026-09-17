package com.robot.platform.mqtt;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;

/** Spring assembly for the optional MQTT boundary; no network client exists while it is disabled. */
@AutoConfiguration
@EnableConfigurationProperties(RobotMqttProperties.class)
public class RobotMqttAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    RobotMessageTypeRegistry robotMessageTypeRegistry(List<RobotMessageDescriptorContributor> contributors) {
        RobotMessageTypeRegistry registry = new RobotMessageTypeRegistry();
        contributors.forEach(contributor -> contributor.contribute(registry));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    RobotMessageEnvelopeCodec robotMessageEnvelopeCodec(RobotMessageTypeRegistry registry, RobotMqttProperties properties) {
        return new RobotMessageEnvelopeCodec(registry, properties.getMaxPayloadBytes(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "robot.mqtt", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    HiveMqRobotMqttClient robotMqttClient(RobotMessageEnvelopeCodec codec, RobotMessageTypeRegistry registry,
                                          RobotMqttProperties properties) {
        properties.requireEnabledBrokerConfiguration();
        if (registry.isEmpty()) throw new IllegalStateException("enabled MQTT requires at least one message descriptor");
        RobotMqttProperties.Cloud cloud = properties.getCloud();
        return HiveMqRobotMqttClient.forBroker(codec, registry, properties.getHost(), properties.getPort(),
                cloud.getClientId(), cloud.getUsername(), cloud.getPassword());
    }

    @Bean
    @ConditionalOnMissingBean(RobotMqttPublisher.class)
    @ConditionalOnProperty(prefix = "robot.mqtt", name = "enabled", havingValue = "true")
    RobotMqttPublisher robotMqttPublisher(HiveMqRobotMqttClient client) { return client; }

    @Bean
    @ConditionalOnMissingBean(RobotMqttSubscriber.class)
    @ConditionalOnProperty(prefix = "robot.mqtt", name = "enabled", havingValue = "true")
    RobotMqttSubscriber robotMqttSubscriber(HiveMqRobotMqttClient client) { return client; }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "robot.mqtt", name = "enabled", havingValue = "true")
    RobotMqttLifecycle robotMqttLifecycle(HiveMqRobotMqttClient client, RobotMessageTypeRegistry registry) {
        if (registry.isEmpty()) throw new IllegalStateException("enabled MQTT requires at least one message descriptor");
        return new RobotMqttLifecycle(client);
    }

    @Bean("robotMqttHealthIndicator")
    @ConditionalOnMissingBean(name = "robotMqttHealthIndicator")
    @ConditionalOnProperty(prefix = "robot.mqtt", name = "enabled", havingValue = "true")
    HealthIndicator robotMqttHealthIndicator(HiveMqRobotMqttClient client) {
        return () -> client.isHealthy() ? Health.up().withDetail("state", client.state().name()).build()
                : Health.down().withDetail("state", client.state().name()).build();
    }
}
