package com.robot.platform.ai.agent;

import com.robot.platform.ai.agent.service.AiMissionSafetyPolicy;
import com.robot.platform.robot.mission.controller.admin.vo.MissionActionReqVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiMissionSafetyPolicyTest {
    private final AiMissionSafetyPolicy policy = new AiMissionSafetyPolicy();

    @Test
    void rejectsArbitraryDeviceActionsAndUnsafeArguments() {
        assertThrows(RuntimeException.class, () -> policy.validate(List.of(action("CUSTOM", "{}"))));
        assertThrows(RuntimeException.class, () -> policy.validate(List.of(action("WAIT", "{\"seconds\":301}"))));
        assertThrows(RuntimeException.class, () -> policy.validate(List.of(action("NAVIGATE", "{\"target\":\"\"}"))));
        assertThrows(RuntimeException.class, () -> policy.validate(List.of(action("PLAY_MEDIA", "{\"mediaUrl\":\"http://example.com/a.mp3\"}"))));
        assertDoesNotThrow(() -> policy.validate(List.of(action("WAIT", "{\"seconds\":30}"), action("RETURN_HOME", "{}"))));
    }

    private static MissionActionReqVO action(String type, String parameters) {
        MissionActionReqVO action = new MissionActionReqVO();
        action.setActionType(type); action.setParameters(parameters);
        return action;
    }
}
