package com.robot.platform.ai.enums;

import com.robot.platform.framework.common.exception.ErrorCode;

public interface AiErrorCodeConstants {
    ErrorCode AI_TENANT_FORBIDDEN = new ErrorCode(1_010_006_000, "无权访问其他租户的 AI 资源");
    ErrorCode AI_PROVIDER_NOT_EXISTS = new ErrorCode(1_010_006_001, "AI 模型供应商不存在");
    ErrorCode AI_PROVIDER_TYPE_INVALID = new ErrorCode(1_010_006_002, "AI 模型供应商类型无效");
    ErrorCode AI_PROVIDER_API_KEY_REQUIRED = new ErrorCode(1_010_006_003, "AI 模型供应商 API Key 未配置");
    ErrorCode AI_MODEL_NOT_EXISTS = new ErrorCode(1_010_006_004, "AI 模型不存在");
    ErrorCode AI_MODEL_TYPE_INVALID = new ErrorCode(1_010_006_005, "AI 模型类型无效");
    ErrorCode AI_PROMPT_NOT_EXISTS = new ErrorCode(1_010_006_006, "AI Prompt 不存在");
    ErrorCode AI_AGENT_NOT_EXISTS = new ErrorCode(1_010_006_007, "AI 智能体不存在");
    ErrorCode AI_AGENT_CONFIG_INVALID = new ErrorCode(1_010_006_008, "AI 智能体模型配置无效");
    ErrorCode AI_AGENT_ROBOT_NOT_BOUND = new ErrorCode(1_010_006_009, "智能体未绑定该机器人");
}
