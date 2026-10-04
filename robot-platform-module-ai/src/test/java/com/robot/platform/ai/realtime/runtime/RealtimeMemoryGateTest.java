package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.service.*;
import com.robot.platform.ai.memory.context.MemoryContextBuilder;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.service.*;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeMemoryGateTest {
    @Test void skippedTurnsReuseCommunicationSnapshotAndForgetClearsIt() {
        var retriever = mock(MemoryRetriever.class);
        when(retriever.retrieveBackground(any(), eq(3))).thenReturn(List.of(
                new MemorySnippet(1,"ROBOT","PREFERENCE","用户希望回答简短",null,1),
                new MemorySnippet(2,"ROBOT","RELATION","用户的猫叫小黑",null,1)));
        var runtime = runtime(retriever);
        ReflectionTestUtils.invokeMethod(runtime, "loadCommunicationContext");
        String greeting = ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "你好");
        assertTrue(greeting.contains("回答简短"));
        assertFalse(greeting.contains("小黑"));
        String fact = ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "什么是 ROS？");
        assertEquals(greeting, fact);
        assertEquals("", ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "忘记我的偏好"));
        assertEquals("", ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "你好"));
        verify(retriever, times(1)).retrieveBackground(any(), eq(3));
        verify(retriever, never()).retrieve(any(), anyInt());
    }

    @Test void onlyCompletedUserEvidenceIsUsedAndNeverSharedAcrossSessions() {
        var retriever = mock(MemoryRetriever.class);
        when(retriever.retrieve(any(), eq(3))).thenReturn(List.of(
                new MemorySnippet(1,"ROBOT","RELATION","用户的猫叫小黑",null,.9)));
        var runtime = runtime(retriever);
        ReflectionTestUtils.setField(runtime, "finalizedUserText", "我家的猫");
        ReflectionTestUtils.setField(runtime, "finalizedAssistantText", "助手编造的名字");
        ReflectionTestUtils.invokeMethod(runtime, "submitFinalizedTurn");
        assertEquals("", ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "它叫什么？"));
        ReflectionTestUtils.setField(runtime, "responseComplete", true);
        ReflectionTestUtils.invokeMethod(runtime, "submitFinalizedTurn");
        String result = ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "它叫什么？");
        assertTrue(result.contains("小黑"));
        verify(retriever).retrieve(argThat(q -> q.text().equals("我家的猫；它叫什么？")), eq(3));
        assertEquals("", ReflectionTestUtils.invokeMethod(runtime(retriever), "buildMemoryContext", "它叫什么？"));
        ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "忘记我的猫");
        assertEquals("", ReflectionTestUtils.invokeMethod(runtime, "buildMemoryContext", "它叫什么？"));
    }

    private static RealtimeAgentRuntime runtime(MemoryRetriever retriever) {
        var runtime = new RealtimeAgentRuntime("test", new DeviceSession(1L, 2L, 3L, "SN", 4, ApiAudience.DEVICE),
                mock(AiAgentRobotBindingService.class), mock(AiAgentService.class), new RealtimeModelRouter(),
                null, null, null, null, new MemoryContextBuilder(retriever, 3));
        ReflectionTestUtils.setField(runtime, "conversationIdentity", ConversationIdentity.anonymous(1,3));
        ReflectionTestUtils.setField(runtime, "agentConfig", new AiAgentConfig(1,1,"a","system",1,1,"CASCADE",1L,null,2L,3L,
                "LONG_TERM", true, false));
        return runtime;
    }
}
