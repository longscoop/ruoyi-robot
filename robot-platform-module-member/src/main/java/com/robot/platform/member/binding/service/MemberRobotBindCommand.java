package com.robot.platform.member.binding.service;

public record MemberRobotBindCommand(long tenantId, long memberId, long robotId, String role, String status) { }
