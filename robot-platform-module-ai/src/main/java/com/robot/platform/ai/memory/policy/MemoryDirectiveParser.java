package com.robot.platform.ai.memory.policy;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MemoryDirectiveParser {
    public enum Directive { REMEMBER, DO_NOT_REMEMBER, FORGET, NORMAL }

    public Directive parse(String text) {
        if (text == null || text.isBlank()) return Directive.NORMAL;
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (containsAny(value, "别记", "不要记", "别保存", "don't remember", "do not remember")) {
            return Directive.DO_NOT_REMEMBER;
        }
        if (containsAny(value, "忘掉", "忘记", "删除记忆", "forget ")) {
            return Directive.FORGET;
        }
        if (containsAny(value, "记住", "记一下", "remember ")) {
            return Directive.REMEMBER;
        }
        return Directive.NORMAL;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
