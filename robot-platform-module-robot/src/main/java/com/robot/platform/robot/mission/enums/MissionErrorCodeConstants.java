package com.robot.platform.robot.mission.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** Mission errors remain stable for Admin, App and later device message handlers. */
public interface MissionErrorCodeConstants {
    ErrorCode MISSION_NOT_EXISTS = new ErrorCode(1_010_004_000, "任务不存在");
    ErrorCode MISSION_INVALID_REQUEST = new ErrorCode(1_010_004_001, "任务请求不合法");
    ErrorCode MISSION_REQUEST_ID_CONFLICT = new ErrorCode(1_010_004_002, "requestId 已对应不同任务请求");
    ErrorCode MISSION_ROBOT_NOT_AVAILABLE = new ErrorCode(1_010_004_003, "机器人不存在或不属于当前租户");
    ErrorCode MISSION_CONCURRENT_MODIFICATION = new ErrorCode(1_010_004_004, "任务状态已被并发修改");
    ErrorCode MISSION_ILLEGAL_TRANSITION = new ErrorCode(1_010_004_005, "任务状态迁移不合法");
    ErrorCode MISSION_WAIT_TIMEOUT = new ErrorCode(1_010_004_006, "任务等待机器人超时");
}
