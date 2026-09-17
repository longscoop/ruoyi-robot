package com.robot.platform.ai.prompt.service;

public record CreatePromptCommand(long tenantId, String name, String code, String type,
                                  String content, String status) {
}
