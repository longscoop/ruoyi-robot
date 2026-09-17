package com.robot.platform.device.product.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** Product-domain errors use the robot platform reserved range. */
public interface ProductErrorCodeConstants {
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_010_001_000, "产品型号不存在");
    ErrorCode PRODUCT_SCOPE_FORBIDDEN = new ErrorCode(1_010_001_001, "无权维护其他租户的产品型号");
    ErrorCode PUBLIC_PRODUCT_PLATFORM_ADMIN_ONLY = new ErrorCode(1_010_001_002, "公共产品型号仅平台管理员可维护");
    ErrorCode PRODUCT_STATUS_INVALID = new ErrorCode(1_010_001_003, "产品型号状态不合法");
}
