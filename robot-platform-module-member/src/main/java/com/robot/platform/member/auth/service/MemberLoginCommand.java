package com.robot.platform.member.auth.service;

/** Tenant is supplied only while unauthenticated; every later request derives it from its session. */
public record MemberLoginCommand(long tenantId, String mobile, String password) { }
