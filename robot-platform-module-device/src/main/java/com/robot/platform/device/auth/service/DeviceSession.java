package com.robot.platform.device.auth.service;

import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import com.robot.platform.security.session.RobotSession;

/** Redis session payload; it contains identity/version metadata but never an HTTP secret or opaque token. */
public record DeviceSession(long tenantId, long deviceId, long robotId, String deviceSn, int credentialVersion,
                            ApiAudience audience) {
    public RobotSession asRobotSession() {
        return new RobotSession(tenantId, deviceId, audience, SubjectType.DEVICE);
    }
}
