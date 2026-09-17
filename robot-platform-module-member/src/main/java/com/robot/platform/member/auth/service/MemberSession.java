package com.robot.platform.member.auth.service;

import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;

/** Session payload deliberately excludes any credential or opaque token. */
public record MemberSession(long tenantId, long memberId, ApiAudience audience, SubjectType subjectType) { }
