package com.robot.platform.tenant.quota.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface TenantQuotaErrorCodeConstants {
    ErrorCode TENANT_QUOTA_NOT_EXISTS = new ErrorCode(1_010_002_000, "租户机器人配额不存在");
    ErrorCode TENANT_USAGE_NOT_EXISTS = new ErrorCode(1_010_002_001, "租户机器人用量不存在");
    ErrorCode ROBOT_QUOTA_EXHAUSTED = new ErrorCode(1_010_002_002, "机器人配额已用尽");
    ErrorCode ROBOT_USAGE_NEGATIVE = new ErrorCode(1_010_002_003, "机器人用量不能为负数");
    ErrorCode ROBOT_LIMIT_BELOW_USAGE = new ErrorCode(1_010_002_004, "机器人配额不能低于当前用量");
}
