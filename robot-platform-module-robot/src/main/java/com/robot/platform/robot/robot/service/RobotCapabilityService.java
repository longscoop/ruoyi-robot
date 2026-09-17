package com.robot.platform.robot.robot.service;

import com.robot.platform.robot.robot.dal.dataobject.RobotCapabilityDO;

import java.util.List;

public interface RobotCapabilityService {
    void upsert(long robotId, String capabilityCode, String configuration);
    List<RobotCapabilityDO> list(long robotId);
}
