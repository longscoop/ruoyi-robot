package com.robot.platform.robot.mission.message;

import com.robot.platform.mqtt.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Declares the versioned Mission protocol in one place before any transport adapter can use it. */
@Configuration(proxyBeanMethods = false)
class MissionMqttProtocolConfiguration {
    @Bean RobotMessageDescriptorContributor missionDescriptor() {
        return registry -> {
            registry.register(MessageType.MISSION_START, 1, MissionStartPayload.class);
            registry.register(MessageType.MISSION_CANCEL, 1, MissionCancelPayload.class);
            registry.register(new RobotMessageDescriptor<>(MessageType.MISSION_ACK, 1, MissionAckPayload.class,
                    Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE), value -> { }));
            registry.register(new RobotMessageDescriptor<>(MessageType.MISSION_EVENT, 1, MissionEventPayload.class,
                    Set.of(MessageSource.ROBOT), Set.of(RobotTopic.Channel.STATE), value -> { }));
        };
    }
}
