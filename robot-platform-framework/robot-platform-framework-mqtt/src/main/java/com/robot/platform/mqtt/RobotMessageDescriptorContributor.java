package com.robot.platform.mqtt;

/** A domain module contributes protocol descriptors without making the transport depend on its DTOs. */
@FunctionalInterface
public interface RobotMessageDescriptorContributor {
    void contribute(RobotMessageTypeRegistry registry);
}
