package com.robot.platform.security;

import com.robot.platform.framework.common.enums.UserTypeEnum;
import com.robot.platform.framework.security.core.LoginUser;
import lombok.Getter;
import lombok.ToString;

/** Explicitly tagged platform principal; its audience prevents token namespace confusion. */
@Getter
@ToString(callSuper = true)
public final class RobotAuthenticatedPrincipal extends LoginUser {
    private final ApiAudience audience;
    private final SubjectType subjectType;

    public RobotAuthenticatedPrincipal(long tenantId, long subjectId, ApiAudience audience, SubjectType subjectType) {
        this.audience = audience;
        this.subjectType = subjectType;
        setId(subjectId);
        setTenantId(tenantId);
        setUserType(subjectType == SubjectType.MEMBER ? UserTypeEnum.MEMBER.getValue() : UserTypeEnum.ADMIN.getValue());
    }
}
