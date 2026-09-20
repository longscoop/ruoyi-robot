package com.robot.platform.ai.memory;

import java.util.Locale;
import java.util.Objects;

public class MemoryScopeValidator {

    public void validate(String scope, Long memberId, Long robotId) {
        Objects.requireNonNull(scope, "scope");
        switch (scope.trim().toUpperCase(Locale.ROOT)) {
            case "MEMBER" -> require(memberId, "MEMBER memory requires memberId");
            case "MEMBER_ROBOT" -> {
                require(memberId, "MEMBER_ROBOT memory requires memberId");
                require(robotId, "MEMBER_ROBOT memory requires robotId");
            }
            case "ROBOT" -> require(robotId, "ROBOT memory requires robotId");
            default -> throw new IllegalArgumentException("Unsupported memory scope: " + scope);
        }
    }

    private static void require(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
