package com.robot.platform.robot.robot.service.command;

import lombok.Data;

/** Management edits deliberately exclude telemetry fields. */
@Data
public class RobotUpdateCommand {
    private String name;
}
