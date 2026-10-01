package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.model.client.ChatRequest.ChatMessage;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local, conservative topic boundaries; no extra model call on the first-audio path. */
final class ConversationContextPolicy {
    enum Mode { INDEPENDENT, FOLLOW_UP, EARLIER_REFERENCE }

    record Selection(Mode mode, List<ChatMessage> messages) {
        String instructions() {
            return switch (mode) {
                case INDEPENDENT -> "本轮是可独立回答的新请求。只回应本轮内容和与本轮有关的检索资料；不要续接旧话题。"
                        + "普通故事、笑话、诗歌使用虚构角色，不自行套用用户、用户亲友或宠物。";
                case FOLLOW_UP -> "本轮可承接当前话题，短回复可能是在回答上一条助手的提问。只用相关上下文，"
                        + "不要为了亲切而带入无关人物、宠物或用户资料。";
                case EARLIER_REFERENCE -> "用户明确回指前文，只使用与该回指有关的问答，不延伸其他旧话题。";
            };
        }
    }

    private static final Pattern EARLIER = Pattern.compile("刚才|刚刚|之前|前面|前一个|最开始|先前|上次");
    private static final Pattern REFERENCE = Pattern.compile(
            "^(继续|接着|然后|还有|为什么|为何|再来|再说|再讲|再写|再换|换一个|换个|再短|再长|说短|说长|"
                    + "展开|详细点|简单点|什么意思|你说什么|你说的|你觉得呢|那|这|它|他|她)"
                    + "|这个|那个|这段|那段|这句|那句|这件|那件|这首|那首|这道|那道|"
                    + "给[它他她]|把[它他她]|关于[它他她]|围绕[它他她]|[它他她]的|"
                    + "^(continue|why|what about|tell me more|make it|that|this|it\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARITHMETIC = Pattern.compile(
            "[0-9零一二两三四五六七八九十百千万点]+(?:加上?|减去?|乘以?|除以?|[+＋×*÷/−-])"
                    + "[0-9零一二两三四五六七八九十百千万点]+");
    private static final Pattern REQUEST = Pattern.compile(
            "(?:讲|说|写|编|来).{0,10}(?:故事|笑话|童话|寓言|诗|新闻|绕口令)|"
                    + "(?:给我|帮我|请|我想|我要|能否|能不能|可以|麻烦).{0,10}"
                    + "(?:介绍|解释|查询|查查|查一下|推荐|计算|算算|算一下|翻译|总结|写|讲|画|设计|制定|规划|播放)|"
                    + "^(?:介绍|解释|查询|推荐|计算|翻译|总结|设计|制定|规划|播放).+|"
                    + ".{2,}(?:是什么|叫什么|在哪里|在哪儿|怎么|怎样|如何|多少|几岁|是谁|多大|什么区别)|"
                    + "(?:天气|温度|几点|日期|星期几|今天几号)|"
                    + "^(?:tell me a |write |translate |calculate |what is |who is |where is ).+",
            Pattern.CASE_INSENSITIVE);

    static Selection select(String question, List<ChatMessage> boundedHistory) {
        Mode mode = classify(question);
        if (mode == Mode.INDEPENDENT) return new Selection(mode, List.of());
        if (mode == Mode.EARLIER_REFERENCE) return new Selection(mode, List.copyOf(boundedHistory));
        // A short reply follows the current topic, not every subject mentioned in this session.
        // Keep all completed turns since the most recent explicit independent request.
        int start = 0;
        for (int i = boundedHistory.size() - 2; i >= 0; i -= 2) {
            if (classify(boundedHistory.get(i).content()) == Mode.INDEPENDENT) { start = i; break; }
        }
        return new Selection(mode, List.copyOf(boundedHistory.subList(start, boundedHistory.size())));
    }

    static Mode classify(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", " ").trim();
        if (EARLIER.matcher(value).find()) return Mode.EARLIER_REFERENCE;
        if (ARITHMETIC.matcher(value.replace(" ", "")).find()) return Mode.INDEPENDENT;
        if (REFERENCE.matcher(value).find()) return Mode.FOLLOW_UP;
        if (REQUEST.matcher(value).find()) return Mode.INDEPENDENT;
        // Ambiguous statements/short replies retain context rather than being cut by lexical overlap.
        return Mode.FOLLOW_UP;
    }
}
