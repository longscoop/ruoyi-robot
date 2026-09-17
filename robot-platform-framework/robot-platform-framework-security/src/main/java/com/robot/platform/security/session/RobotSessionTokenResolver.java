package com.robot.platform.security.session;

/** Resolves only platform-owned opaque tokens; null is an unauthenticated or malformed token. */
public interface RobotSessionTokenResolver {
    RobotSession resolveSession(String token);
}
