package com.robot.platform.ai.realtime.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** Direct voice commands only: do not interpret questions or quoted examples as exit commands. */
public final class ConversationExitIntent {
    private ConversationExitIntent() { }
    private static final Pattern COMMAND = Pattern.compile(
            "(?:(?:好了|好的|好|那|现在|请|麻烦|你|您|小智|小志|小七))*"
            + "(?:再见|拜拜|退出(?:对话|会话|聊天)?|结束(?:对话|会话|聊天)|停止(?:对话|会话|聊天)|不聊了|不说了|退一下|退下|不用(?:回复|回答)了|不要再说话了|别再说话了|goodbye)"
            + "(?:(?:一下|吧|啊|呀|啦|了|哦|呢|小智|小志|小七))*"
            + "(?:不要再说话了|别再说话了|不用回复了)?");

    public static boolean matches(String text) {
        if (text == null || text.isBlank()) return false;
        // A command at the beginning still applies if the microphone captures later background speech.
        String first = text.strip().split("[。.!！?？;；\\n]", 2)[0];
        if (text.strip().startsWith(first + "？") || text.strip().startsWith(first + "?")) return false;
        return COMMAND.matcher(first.replaceAll("[\\p{P}\\p{Z}\\s]", "").toLowerCase(Locale.ROOT)).matches();
    }
}
