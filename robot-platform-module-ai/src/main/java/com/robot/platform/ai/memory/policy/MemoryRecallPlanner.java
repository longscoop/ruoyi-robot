package com.robot.platform.ai.memory.policy;

import java.util.regex.Pattern;

/** Stateless, bounded, local-only gate and rewrite. Never changes identity or provider filters. */
public final class MemoryRecallPlanner {
    private static final int MAX_INPUT = 2048;
    private static final int MAX_ANTECEDENT = 256;
    private static final MemoryDirectiveParser DIRECTIVES = new MemoryDirectiveParser();
    private static final Pattern SMALL_TALK = Pattern.compile(
            "(?i)^(你好|您好|早上好|晚上好|谢谢|谢谢你|好的|好吧|再见|晚安|哈哈|嗯|hello|hi|thanks|thank you|bye)[\\p{P}\\s]*$");
    private static final Pattern PERSONAL = Pattern.compile(
            "我(的|家|之前|以前|上次|喜欢|不喜欢|习惯|适合|能不能|可以|应该)|我们(家|之前|上次)|"
            + "你(还)?记得|之前.*(告诉|说过|聊过)|上次.*(说|聊|选|决定)|按.*(偏好|习惯)|"
            + "(?i:\\b(my|mine|our)\\b|do you remember|last time|i (like|prefer|usually))");
    private static final Pattern REFERENCE = Pattern.compile(
            "^(那|这)?(它|他|她|这个|那个|这件事|那件事|这种|那种)|^(那|那么)?(还适合我|适合我吗)|"
            + "(?i:^(what about|how about) (it|that|this)\\b|^(it|that|this)\\b)");
    private static final Pattern GENERIC = Pattern.compile(
            "是什么|是什么意思|什么是|为什么|怎么(安装|配置|使用|实现)|如何(安装|配置|使用|实现)|"
            + "谁(是|发明)|首都|几加几|讲个.*故事|讲一个.*故事|讲个笑话|"
            + "(?i:^(what is|what are|who is|why |how (to|does)|tell me a (joke|story)))");
    private static final Pattern ARITHMETIC = Pattern.compile("^[\\d\\s.+*/×÷=？?()-]+$");

    private MemoryRecallPlanner() { }

    public record Plan(boolean retrieve, String query, String reason) { }

    public static Plan plan(String text, String previousUserText) {
        if (text == null || text.isBlank()) return skip("empty");
        // Do not truncate a potentially meaningful negation or a forget directive.
        if (text.length() > MAX_INPUT) return skip("input_too_long");
        String query = text.strip().replaceAll("\\s+", " ");
        if (DIRECTIVES.parse(query) == MemoryDirectiveParser.Directive.FORGET) return skip("forget");
        if (SMALL_TALK.matcher(query).matches()) return skip("small_talk");
        if (REFERENCE.matcher(query).find()) {
            // Only the immediately preceding completed user turn is allowed, never model output
            // or another session. Ambiguous references are left to short-term conversation context.
            if (previousUserText == null || previousUserText.length() > MAX_ANTECEDENT
                    || !PERSONAL.matcher(previousUserText).find()
                    || REFERENCE.matcher(previousUserText.strip()).find()
                    || DIRECTIVES.parse(previousUserText) != MemoryDirectiveParser.Directive.NORMAL) {
                return skip("unresolved_reference");
            }
            return new Plan(true, previousUserText.strip().replaceAll("\\s+", " ")
                    + "；" + query, "session_reference");
        }
        if (PERSONAL.matcher(query).find()) return new Plan(true, rewrite(query), "personal");
        if (GENERIC.matcher(query).find() || ARITHMETIC.matcher(query).matches()) return skip("general_question");
        // Uncertain queries retain existing recall behavior to avoid silently losing useful memory.
        return new Plan(true, rewrite(query), "uncertain");
    }

    private static String rewrite(String query) {
        String rewritten = query.replaceFirst("^(请问[，,：: ]*|麻烦问一下[，,：: ]*)", "")
                .replaceFirst("^(你还记得|你记得)[，,：: ]*", "")
                .replaceFirst("(?i)^do you remember\\s+", "");
        return rewritten.isBlank() ? query : rewritten;
    }

    private static Plan skip(String reason) { return new Plan(false, "", reason); }
}
