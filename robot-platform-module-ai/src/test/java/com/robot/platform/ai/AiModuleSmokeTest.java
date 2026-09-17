package com.robot.platform.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiModuleSmokeTest {

    @Test
    void configurationTypeExists() {
        assertNotNull(new AiModuleConfiguration());
    }
}
