package com.robot.platform.device.device.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface DeviceErrorCodeConstants {
    ErrorCode DEVICE_NOT_EXISTS = new ErrorCode(1_010_002_000, "设备不存在");
    ErrorCode DEVICE_SCOPE_FORBIDDEN = new ErrorCode(1_010_002_001, "无权访问其他租户的设备");
    ErrorCode DEVICE_SERIAL_EXISTS = new ErrorCode(1_010_002_002, "设备序列号已存在");
    ErrorCode DEVICE_LIFECYCLE_INVALID = new ErrorCode(1_010_002_003, "设备生命周期转换不合法");
    ErrorCode DEVICE_NOT_UNACTIVATED = new ErrorCode(1_010_002_004, "设备不是待激活状态");
    ErrorCode DEVICE_NOT_ACTIVATED = new ErrorCode(1_010_002_005, "设备尚未激活");
    ErrorCode DEVICE_TENANT_REQUIRED = new ErrorCode(1_010_002_006, "需要已认证的租户上下文");
    ErrorCode DEVICE_GROUP_SCOPE_FORBIDDEN = new ErrorCode(1_010_002_007, "设备组和设备必须属于同一租户");
    ErrorCode DEVICE_GROUP_NOT_EXISTS = new ErrorCode(1_010_002_008, "设备组不存在");
}
