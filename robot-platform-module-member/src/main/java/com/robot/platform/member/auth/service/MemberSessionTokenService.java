package com.robot.platform.member.auth.service;

public interface MemberSessionTokenService {
    String issue(MemberSession session);
    MemberSession resolve(String token);
    void revoke(String token);
}
