package com.robot.platform.ai.memory.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRecallPlannerTest {
    @ParameterizedTest
    @ValueSource(strings = {"你好！", "谢谢", "hi", "法国的首都是什么？", "猫为什么怕水？",
            "什么是 MQTT？", "怎么安装 Java？", "1 + 1 = ?", "讲个笑话", "What is ROS?",
            "忘记我的工作", "请删除记忆", "forget my name", "它叫什么？", "这个适合我吗？", ""})
    void skipsUnneededOrUnresolvedRecall(String text) {
        assertFalse(MemoryRecallPlanner.plan(text, null).retrieve(), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"我家的猫叫什么？", "我的工作是什么？", "我适合喝什么？",
            "按我的偏好推荐饮料", "你记得我的名字吗？", "以后请用中文回答", "咖啡", "my preferences"})
    void retainsPersonalAndUncertainQueries(String text) {
        assertTrue(MemoryRecallPlanner.plan(text, null).retrieve(), text);
    }

    @Test void rewritesWithoutInventingFactsOrLosingNegation() {
        assertEquals("我不喜欢什么饮料？", MemoryRecallPlanner.plan("请问，你还记得我不喜欢什么饮料？", null).query());
        var plan = MemoryRecallPlanner.plan("它叫什么？", "我家的猫");
        assertEquals("我家的猫；它叫什么？", plan.query());
        assertEquals("session_reference", plan.reason());
    }

    @Test void doesNotReuseUnrelatedPrivateOrAmbiguousAntecedents() {
        for (String previous : new String[] {"法国的首都是什么", "不要记住我的名字", "忘记我的猫", "它是我的猫", "我的" + "猫".repeat(300)}) {
            assertFalse(MemoryRecallPlanner.plan("它叫什么？", previous).retrieve());
        }
        assertFalse(MemoryRecallPlanner.plan(null, "我家的猫").retrieve());
        assertFalse(MemoryRecallPlanner.plan("我".repeat(2049), null).retrieve());
        assertFalse(MemoryRecallPlanner.plan("忘记它", "我家的猫").retrieve());
    }
}
