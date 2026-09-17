package com.robot.platform.robot.robot.enums;

import com.robot.platform.framework.common.exception.ErrorCode;

/** Error codes for tenant-owned robot resources. */
public interface RobotErrorCodeConstants {
    ErrorCode ROBOT_NOT_EXISTS = new ErrorCode(1_010_003_000, "机器人不存在");
    ErrorCode ROBOT_CODE_DUPLICATE = new ErrorCode(1_010_003_001, "机器人编号在当前租户内已存在");
    ErrorCode ROBOT_DEVICE_ALREADY_PROVISIONED = new ErrorCode(1_010_003_002, "设备已绑定机器人");
    ErrorCode ROBOT_DELETE_REQUIRES_DEPROVISION = new ErrorCode(1_010_003_003, "设备绑定的机器人只能通过设备解绑流程释放");
    ErrorCode ROBOT_TENANT_MISMATCH = new ErrorCode(1_010_003_004, "机器人租户上下文不匹配");
    ErrorCode ROBOT_BATTERY_INVALID = new ErrorCode(1_010_003_005, "机器人电量必须在 0 到 100 之间");
    ErrorCode ROBOT_CAPABILITY_DUPLICATE = new ErrorCode(1_010_003_006, "机器人能力已存在");
}
