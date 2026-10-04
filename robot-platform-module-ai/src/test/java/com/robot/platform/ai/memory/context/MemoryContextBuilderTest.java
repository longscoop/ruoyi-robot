package com.robot.platform.ai.memory.context;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryContextBuilderTest {
    @Test void fallbackPathAlsoGatesAndRewrites() {
        MemoryRetriever retriever = mock(MemoryRetriever.class);
        MemoryContextBuilder builder = new MemoryContextBuilder(retriever, 3);
        var identity = ConversationIdentity.anonymous(1, 2);
        for (String text : List.of("你好", "什么是机器人", "忘记我的猫", "它叫什么")) {
            assertEquals("", builder.buildContext(identity, agent(true), text));
        }
        verifyNoInteractions(retriever);
        when(retriever.retrieve(any(), eq(3))).thenReturn(List.of(
                new MemorySnippet(1,"ROBOT","RELATION","用户的猫叫小黑",null,.9)));
        assertTrue(builder.buildContext(identity, agent(true), "它叫什么", "我家的猫").contains("小黑"));
        verify(retriever).retrieve(argThat(q -> q.text().equals("我家的猫；它叫什么")
                && q.tenantId() == 1 && q.robotId() == 2 && q.memberId() == null), eq(3));
    }

    @Test void disabledMemoryReturnsEmptyWithoutQuery() {
        MemoryRetriever retriever = mock(MemoryRetriever.class);
        MemoryContextBuilder builder = new MemoryContextBuilder(retriever, 8);
        assertEquals("", builder.buildContext(ConversationIdentity.anonymous(1, 2), agent(false), "咖啡"));
        verifyNoInteractions(retriever);
    }

    @Test void anonymousNeverPassesMemberIdToRetriever() {
        MemoryRetriever retriever = mock(MemoryRetriever.class);
        when(retriever.retrieve(any(), eq(8))).thenReturn(List.of(new MemorySnippet(1,"ROBOT","FACT","充电桩在客厅",null,.8)));
        String context = new MemoryContextBuilder(retriever, 8)
                .buildContext(ConversationIdentity.anonymous(1, 2), agent(true), "充电");
        assertTrue(context.contains("充电桩在客厅"));
        verify(retriever).retrieve(argThat(q -> q.memberId() == null && q.robotId() == 2), eq(8));
    }

    @Test void identifiedMemberGetsDelimitedTopKContext() {
        MemoryRetriever retriever = mock(MemoryRetriever.class);
        when(retriever.retrieve(any(), eq(2))).thenReturn(List.of(
                new MemorySnippet(1,"MEMBER","PREFERENCE","咖啡少糖",null,.9),
                new MemorySnippet(2,"MEMBER_ROBOT","HABIT","晚上巡检",null,.8)));
        String context = new MemoryContextBuilder(retriever, 2).buildContext(
                new ConversationIdentity(1,2,3L,"FACE",.95,true), agent(true), "咖啡");
        assertTrue(context.startsWith("<retrieved_memory>"));
        assertTrue(context.contains("咖啡少糖"));
        assertTrue(context.endsWith("</retrieved_memory>"));
        verify(retriever).retrieve(argThat(q -> Long.valueOf(3).equals(q.memberId())), eq(2));
    }

    private static AiAgentConfig agent(boolean enabled) {
        return new AiAgentConfig(1,1,"a","system",1,1,"CASCADE",1L,null,2L,3L,
                "LONG_TERM", enabled, true);
    }
    @Test
    void nativeBackgroundIncludesCommunicationPreferencesButNeverTopicFacts() {
        MemoryRetriever retriever = mock(MemoryRetriever.class);
        when(retriever.retrieveBackground(any(), eq(8))).thenReturn(List.of(
                new MemorySnippet(1,"ROBOT","RELATION","用户家有一只名叫小黑的猫",null,1),
                new MemorySnippet(2,"ROBOT","WORK","用户开发机器人",null,1),
                new MemorySnippet(3,"ROBOT","PREFERENCE","用户希望回答简短",null,1)));
        String text=new MemoryContextBuilder(retriever,8).buildBackgroundContext(
                ConversationIdentity.anonymous(1,33),agent(true));
        assertFalse(text.contains("小黑"));assertFalse(text.contains("开发机器人"));assertTrue(text.contains("回答简短"));
    }

}
