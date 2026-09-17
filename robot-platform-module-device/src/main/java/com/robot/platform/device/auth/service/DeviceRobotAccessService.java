package com.robot.platform.device.auth.service;

import com.robot.platform.security.ApiAudience;
import org.springframework.stereotype.Service;

/** Explicit robot-binding guard for future DEVICE APIs. */
@Service
public final class DeviceRobotAccessService {
    public boolean canAccess(DeviceSession session, long robotId) {
        return session != null && session.audience() == ApiAudience.DEVICE && session.robotId() == robotId;
    }
}
