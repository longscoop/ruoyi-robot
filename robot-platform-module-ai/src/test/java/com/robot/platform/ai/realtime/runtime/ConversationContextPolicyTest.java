package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.model.client.ChatRequest.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationContextPolicyTest {
    private static final List<ChatMessage> HISTORY = List.of(
            new ChatMessage("user", "我们家的猫叫什么名字？"), new ChatMessage("assistant", "小黑。"),
            new ChatMessage("user", "给我讲一个故事。"), new ChatMessage("assistant", "狐狸煮了一锅面，你猜它吃了吗？"),
            new ChatMessage("user", "吃了吧。"), new ChatMessage("assistant", "对，吃得一干二净。"));

    @ParameterizedTest
    @ValueSource(strings = {"一加一等于1。", "那一加一等于几？", "1 + 1 等于几？", "给我讲一个故事。",
            "讲个笑话", "帮我写一首春天的诗", "北京天气怎么样", "法国首都在哪里", "解释光合作用",
            "Tell me a story", "What is 2 + 2?"})
    void selfContainedRequestsDoNotCarryOldTopics(String question) {
        var selected = ConversationContextPolicy.select(question, HISTORY);
        assertEquals(ConversationContextPolicy.Mode.INDEPENDENT, selected.mode());
        assertTrue(selected.messages().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"吃了吧。", "对呀", "没有", "甜的", "然后呢", "还有呢", "为什么", "继续",
            "再讲一个", "给它起个名字", "把这段改短一点", "Why?", "Continue", "Make it shorter"})
    void ellipticalRepliesKeepTheCurrentTopicWithoutThePet(String question) {
        var selected = ConversationContextPolicy.select(question, HISTORY);
        assertEquals(ConversationContextPolicy.Mode.FOLLOW_UP, selected.mode());
        assertEquals(HISTORY.subList(2, 6), selected.messages());
    }

    @Test void explicitReferenceCanReturnToAnEarlierTopicAfterAnIndependentQuestion() {
        var recent = new java.util.ArrayList<>(HISTORY);
        recent.add(new ChatMessage("user", "一加一等于几？")); recent.add(new ChatMessage("assistant", "二。"));
        assertEquals(recent.subList(6, 8), ConversationContextPolicy.select("为什么", recent).messages());
        var selection = ConversationContextPolicy.select("刚才那个故事叫什么名字", recent);
        assertEquals(ConversationContextPolicy.Mode.EARLIER_REFERENCE, selection.mode());
        assertTrue(selection.messages().stream().anyMatch(m -> m.content().contains("狐狸")));
    }
}
