package com.robot.platform.ai.memory.provider;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.MemorySnippet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryRecallGateTest {
    private static AiAgentConfig agent() {
        return new AiAgentConfig(1,1,"a","system",1,1,"CASCADE",1L,null,2L,3L,
                "LONG_TERM", true, true);
    }

    @Test void skippedTurnsNeverCallProviderAndStillValidateNamespace() {
        var provider = mock(MemoryProvider.class);
        when(provider.name()).thenReturn("mysql");
        var registry = new MemoryProviderRegistry(List.of(provider), new MemoryProviderProperties());
        try {
            for (String question : List.of("你好", "什么是 ROS？", "1+1", "它叫什么？", "忘记我的猫", "")) {
                assertTrue(registry.queryMemory(ConversationIdentity.anonymous(1,2), agent(), question, 3).isEmpty());
            }
            verify(provider, never()).queryMemory(any(), any(), any(), anyInt());
            assertThrows(IllegalArgumentException.class, () ->
                    registry.queryMemory(ConversationIdentity.anonymous(2,2), agent(), "你好", 3));
        } finally { registry.destroy(); }
    }

    @Test void rewrittenQueryUsesSameIdentityAndSecondStageFiltering() {
        var provider = mock(MemoryProvider.class);
        when(provider.name()).thenReturn("mysql");
        var identity = new ConversationIdentity(1,2,3L,"FACE",.99,true);
        var agent = agent();
        when(provider.queryMemory(eq(identity), eq(agent), eq("我家的猫；它叫什么？"), eq(16)))
                .thenReturn(List.of(new MemorySnippet(1,"MEMBER","RELATION","用户的猫叫小黑",null,.9),
                        new MemorySnippet(2,"MEMBER","WORK","用户从事会计工作",null,.99)));
        var registry = new MemoryProviderRegistry(List.of(provider), new MemoryProviderProperties());
        try {
            var result = registry.queryMemory(identity, agent, "它叫什么？", 3, "我家的猫");
            assertEquals(List.of("用户的猫叫小黑"), result.stream().map(MemorySnippet::content).toList());
            verify(provider).queryMemory(eq(identity), eq(agent), eq("我家的猫；它叫什么？"), eq(16));
        } finally { registry.destroy(); }
    }

    @Test void explicitBackgroundRetainsOnlyCommunicationPreferences() {
        var provider = mock(MemoryProvider.class);
        when(provider.name()).thenReturn("mysql");
        when(provider.queryMemory(any(), any(), eq(""), eq(16))).thenReturn(List.of(
                new MemorySnippet(1,"ROBOT","PREFERENCE","用户希望回答简短",null,1),
                new MemorySnippet(2,"ROBOT","RELATION","用户的猫叫小黑",null,1)));
        var registry = new MemoryProviderRegistry(List.of(provider), new MemoryProviderProperties());
        try {
            assertEquals(List.of("用户希望回答简短"), registry.queryBackground(
                    ConversationIdentity.anonymous(1,2), agent(), 3).stream().map(MemorySnippet::content).toList());
        } finally { registry.destroy(); }
    }
}
