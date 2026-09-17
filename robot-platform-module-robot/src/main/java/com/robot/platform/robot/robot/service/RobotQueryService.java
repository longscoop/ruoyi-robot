package com.robot.platform.robot.robot.service;

import com.robot.platform.robot.status.model.RobotLiveStatus;

public interface RobotQueryService {
    RobotLiveStatus getStatus(long id);
}
