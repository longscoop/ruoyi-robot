package com.robot.platform.robot.robot.enums;

/** The work state describes what a robot is doing even when its connection changes. */
public enum RobotWorkStatus {
    /** Legacy values remain readable for already provisioned rows and older robots. */
    IDLE, WORKING, CHARGING, FAULT,
    NAVIGATING, INSPECTING, ERROR, UPGRADING
}
