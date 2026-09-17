package com.robot.platform.member.member.enums;

import com.robot.platform.framework.common.exception.ErrorCode;

/** Member-domain errors deliberately do not disclose password or token material. */
public interface MemberErrorCodeConstants {
    ErrorCode MEMBER_LOGIN_INVALID = new ErrorCode(1_010_003_000, "手机号或密码错误");
    ErrorCode MEMBER_DISABLED = new ErrorCode(1_010_003_001, "会员账户已停用");
    ErrorCode MEMBER_NOT_EXISTS = new ErrorCode(1_010_003_002, "会员不存在");
    ErrorCode MEMBER_TENANT_FORBIDDEN = new ErrorCode(1_010_003_003, "无权访问其他租户的会员");
    ErrorCode MEMBER_ROBOT_ACCESS_DENIED = new ErrorCode(1_010_003_004, "无权访问该机器人");
    ErrorCode MEMBER_BINDING_INVALID = new ErrorCode(1_010_003_005, "会员机器人绑定无效");
}
