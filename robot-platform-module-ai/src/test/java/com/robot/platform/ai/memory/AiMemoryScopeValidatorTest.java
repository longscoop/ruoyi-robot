package com.robot.platform.ai.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiMemoryScopeValidatorTest {

    private final MemoryScopeValidator validator = new MemoryScopeValidator();

    @Test
    void enforcesScopeIdentityInvariants() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("MEMBER", null, 10L));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("MEMBER_ROBOT", 20L, null));
        assertDoesNotThrow(() -> validator.validate("MEMBER", 20L, null));
        assertDoesNotThrow(() -> validator.validate("MEMBER_ROBOT", 20L, 10L));
        assertDoesNotThrow(() -> validator.validate("ROBOT", null, 10L));
    }

    @Test
    void rejectsUnknownOrNonPositiveIdentity() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("SESSION", 20L, 10L));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("MEMBER", 0L, null));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("ROBOT", null, -1L));
    }
}
