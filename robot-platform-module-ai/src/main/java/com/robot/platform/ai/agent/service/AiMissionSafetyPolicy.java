package com.robot.platform.ai.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.framework.common.util.json.JsonUtils;
import com.robot.platform.robot.mission.controller.admin.vo.MissionActionReqVO;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Additional fail-closed limits for untrusted AI workflow action proposals. */
@Component
public class AiMissionSafetyPolicy {
    private static final Set<String> ACTIONS = Set.of("NAVIGATE", "SPEAK", "PLAY_MEDIA", "CAPTURE_IMAGE",
            "INSPECT", "FIND_PERSON", "FIND_OBJECT", "RETURN_HOME", "WAIT");

    public void validate(List<MissionActionReqVO> actions) {
        if (actions == null || actions.isEmpty() || actions.size() > 20) {
            throw invalidParamException("AI mission must have 1 to 20 actions");
        }
        for (MissionActionReqVO action : actions) {
            if (action == null || action.getActionType() == null || !ACTIONS.contains(action.getActionType()) || action.getParameters() == null
                    || action.getParameters().length() > 4096) {
                throw invalidParamException("AI mission contains an unsupported action");
            }
            JsonNode parameters;
            try { parameters = JsonUtils.parseTree(action.getParameters()); }
            catch (RuntimeException invalid) { throw invalidParamException("AI mission parameters must be JSON"); }
            if (parameters == null || !parameters.isObject()) {
                throw invalidParamException("AI mission parameters must be a JSON object");
            }
            switch (action.getActionType()) {
                case "NAVIGATE" -> {
                    String target = parameters.path("target").asText("");
                    if (target.isBlank() || target.length() > 128) throw invalidParamException("Invalid navigation target");
                }
                case "SPEAK" -> {
                    String text = parameters.path("text").asText("");
                    if (text.isBlank() || text.length() > 500) throw invalidParamException("Invalid speech text");
                }
                case "PLAY_MEDIA" -> {
                    try {
                        URI url = URI.create(parameters.path("mediaUrl").asText(""));
                        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                                || url.getUserInfo() != null) throw new IllegalArgumentException();
                    } catch (RuntimeException invalid) { throw invalidParamException("AI media URL must be HTTPS"); }
                }
                case "WAIT" -> {
                    JsonNode seconds = parameters.path("seconds");
                    if (!seconds.isIntegralNumber() || !seconds.canConvertToInt()
                            || seconds.asInt() < 1 || seconds.asInt() > 300) {
                        throw invalidParamException("AI wait duration must be 1 to 300 seconds");
                    }
                }
                default -> { }
            }
        }
    }
}
