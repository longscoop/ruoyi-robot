package com.robot.platform.robot.status.mqtt;

import com.robot.platform.mqtt.*;
import com.robot.platform.robot.status.model.HeartbeatPayload;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
class HeartbeatMqttProtocolConfiguration {
    @Bean RobotMessageDescriptorContributor heartbeatDescriptor() {
        return registry -> registry.register(new RobotMessageDescriptor<>(MessageType.HEARTBEAT, 1, HeartbeatPayload.class,
                Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE), payload -> { }));
    }
}
