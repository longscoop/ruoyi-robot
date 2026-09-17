package com.robot.platform.security.session;

import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;

/** A validated, typed opaque-session projection suitable for the security filter. */
public record RobotSession(long tenantId, long subjectId, ApiAudience audience, SubjectType subjectType) { }
