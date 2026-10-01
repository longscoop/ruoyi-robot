package com.robot.platform.ai.memory.service;

import java.util.Map;
import java.util.Locale;

/** Existing memory types remain readable and editable; new extraction uses the six main categories. */
public final class MemoryCategories {
    private MemoryCategories() { }
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("PREFERENCE", "用户偏好"), Map.entry("IDENTITY", "身份信息"),
            Map.entry("CONTEXT", "情景"), Map.entry("WORK", "工作"),
            Map.entry("RELATION", "关系"), Map.entry("FACT", "事实"),
            Map.entry("PROFILE", "身份信息"), Map.entry("HABIT", "习惯"),
            Map.entry("ENVIRONMENT", "情景"), Map.entry("INSTRUCTION", "用户要求"),
            Map.entry("EVENT", "临时事件"), Map.entry("SUMMARY", "摘要"));
    public static boolean communicationPreference(String type, String content) {
        return type != null && java.util.Set.of("PREFERENCE", "INSTRUCTION", "HABIT").contains(type)
                && content != null && java.util.regex.Pattern.compile(
                "称呼|叫我|不要提|不再提|少提|不喜欢.*(提|说)|避免.*(提|说)|回答.*(简短|简洁)|说话.*(方式|风格)|用.*(中文|英文|普通话)")
                .matcher(content).find();
    }
    public static String label(String type) { return LABELS.getOrDefault(type, "其他"); }
    public static String validate(String type) {
        if (type == null) return null;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!LABELS.containsKey(normalized)) throw new IllegalArgumentException("Unsupported memory category");
        return normalized;
    }
}
