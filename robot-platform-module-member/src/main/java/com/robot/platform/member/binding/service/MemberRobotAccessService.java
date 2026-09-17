package com.robot.platform.member.binding.service;

/** Robot-module consumers call this before reading or controlling a robot. */
public interface MemberRobotAccessService {
    void requireReadable(long tenantId, long memberId, long robotId);
    void requireControllable(long tenantId, long memberId, long robotId);
}
