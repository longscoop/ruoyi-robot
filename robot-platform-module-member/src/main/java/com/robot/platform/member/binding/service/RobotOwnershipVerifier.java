package com.robot.platform.member.binding.service;

/** Port implemented by Robot because Robot owns the tenant relationship for robot rows. */
public interface RobotOwnershipVerifier {
    void requireOwnedByTenant(long tenantId, long robotId);
}
