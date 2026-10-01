package com.robot.platform.ai.realtime.runtime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class ConversationExitIntentTest {
    @ParameterizedTest
    @ValueSource(strings={"你退出吧。", "你退一下。", "小智，再见！", "小志，你退下，不要再说话了。后面是背景声", "请结束对话", "不要再说话了", "再见小七", "goodbye"})
    void acceptsDirectCommands(String text) { assertTrue(ConversationExitIntent.matches(text)); }
    @ParameterizedTest
    @ValueSource(strings={"怎么退出？", "你退出了吗？", "不要退出", "我退出公司了", "说一下退出的功能", "播放一首再见", "继续聊天", "你退出吧？", "如果我说你退出吧会怎么样"})
    void keepsQuestionsNegationsAndUnrelatedTopics(String text) { assertFalse(ConversationExitIntent.matches(text)); }
}
