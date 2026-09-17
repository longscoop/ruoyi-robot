package com.robot.platform.member.auth.service;

/** Extensible member authentication boundary; password authentication is the initial provider. */
public interface MemberAuthenticationProvider {
    MemberSession authenticate(MemberLoginCommand command);
}
