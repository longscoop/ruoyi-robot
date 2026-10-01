package com.robot.platform.ai.digitalhuman.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.framework.common.util.json.JsonUtils;

import java.util.Locale;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Stored inside configJson.rendering; absent configuration preserves existing clients. */
public record DigitalHumanRenderConfig(String provider, String service, String avatarId) {
    public static DigitalHumanRenderConfig parse(String json) {
        if (json == null || json.isBlank()) return builtin();
        try {
            JsonNode root = JsonUtils.parseTree(json);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            JsonNode node = root.get("rendering");
            if (node == null || node.isNull()) return builtin();
            if (!node.isObject()) throw new IllegalArgumentException();
            String provider = text(node, "provider", "BUILTIN").toUpperCase(Locale.ROOT);
            String service = text(node, "service", "default");
            String avatar = text(node, "avatarId", "");
            if (!service.matches("[a-zA-Z0-9_-]{1,64}")
                    || (!avatar.isEmpty() && !avatar.matches("[a-zA-Z0-9_-]{1,128}"))) {
                throw new IllegalArgumentException();
            }
            return new DigitalHumanRenderConfig(provider, service, avatar);
        } catch (RuntimeException error) {
            throw invalidParamException("数字人渲染配置无效，请检查服务和形象 ID");
        }
    }

    private static String text(JsonNode node, String key, String fallback) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return fallback;
        if (!value.isTextual()) throw new IllegalArgumentException();
        if (value.asText().isBlank()) return fallback;
        return value.asText().trim();
    }

    public static DigitalHumanRenderConfig builtin() {
        return new DigitalHumanRenderConfig("BUILTIN", "default", "");
    }
}
