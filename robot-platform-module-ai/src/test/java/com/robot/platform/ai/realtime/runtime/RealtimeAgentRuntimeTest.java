package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.realtime.protocol.RealtimeAudioFormat;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeAgentRuntimeTest {

    @Mock private AiAgentRobotBindingService bindingService;
    @Mock private AiAgentService agentService;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void sessionStartUsesTrustedDeviceTenantAndRobotAndKeepsCandidateIdentityUntrusted() {
        DeviceSession device = deviceSession();
        AiAgentDO agent = agentRow();
        AiAgentConfig config = agentConfig("AUTO");
        when(bindingService.requireAgentForRobot(11L, 33L, "xiaoyou")).thenAnswer(invocation -> {
            assertEquals(11L, TenantContextHolder.getRequiredTenantId());
            return agent;
        });
        when(agentService.getResolvedConfig(11L, 101L)).thenAnswer(invocation -> {
            assertEquals(11L, TenantContextHolder.getRequiredTenantId());
            return config;
        });
        RealtimeAgentRuntime runtime = runtime(device);

        runtime.acceptControl(new RealtimeClientEvent.SessionStartEvent(
                "xiaoyou",
                new RealtimeClientEvent.CandidateIdentity(999L, "VOICEPRINT", 0.94),
                new RealtimeAudioFormat("PCM_S16LE", 16000, 1)));

        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
        assertSame(config, runtime.agentConfig());
        assertEquals(999L, runtime.candidateIdentity().memberId());
        assertNull(runtime.effectiveMemberId());
        assertEquals(RealtimeRoute.Mode.NATIVE, runtime.route().mode());
        assertEquals("AUTO_NATIVE", runtime.route().routeReason());
        assertNull(TenantContextHolder.getTenantId());
        verify(bindingService).requireAgentForRobot(11L, 33L, "xiaoyou");
        verify(agentService).getResolvedConfig(11L, 101L);
    }

    @Test
    void stateMachineRejectsInvalidTransitionsAndAcceptsAudioOnlyWhileUserSpeaking() {
        stubStart();
        RealtimeAgentRuntime runtime = runtime(deviceSession());

        runtime.acceptControl(startEvent());
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
        assertThrows(IllegalStateException.class,
                () -> runtime.acceptAudio(ByteBuffer.wrap(new byte[]{1})));
        assertThrows(IllegalStateException.class,
                () -> runtime.acceptControl(new RealtimeClientEvent.SpeechStoppedEvent("evt-x")));

        runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("evt-1"));
        assertEquals(RealtimeAgentRuntime.State.USER_SPEAKING, runtime.state());
        assertDoesNotThrow(() -> runtime.acceptAudio(ByteBuffer.wrap(new byte[]{1, 2, 3})));

        runtime.acceptControl(new RealtimeClientEvent.SpeechStoppedEvent("evt-2"));
        assertEquals(RealtimeAgentRuntime.State.ASSISTANT_RESPONDING, runtime.state());

        runtime.completeAssistantResponse();
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());

        runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "done"));
        runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "duplicate"));
        assertEquals(RealtimeAgentRuntime.State.CLOSED, runtime.state());
        assertThrows(IllegalStateException.class,
                () -> runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("late")));
    }

    @Test
    void closeEventMovesRuntimeToClosed() {
        stubStart();
        RealtimeAgentRuntime runtime = runtime(deviceSession());
        runtime.acceptControl(startEvent());

        runtime.acceptControl(new RealtimeClientEvent.SessionCloseEvent("CLIENT_CLOSE"));

        assertEquals(RealtimeAgentRuntime.State.CLOSED, runtime.state());
        assertEquals("CLIENT_CLOSE", runtime.closeReason().reason());
    }

    @Test
    void managerOwnsRuntimeByWebSocketSessionId() {
        RealtimeAgentRuntimeManager manager = new RealtimeAgentRuntimeManager(
                bindingService, agentService, new RealtimeModelRouter());

        manager.open("ws-1", deviceSession());
        RealtimeAgentRuntime runtime = manager.require("ws-1");

        assertSame(runtime, manager.require("ws-1"));
        assertThrows(IllegalStateException.class, () -> manager.open("ws-1", deviceSession()));
        assertSame(runtime, manager.remove("ws-1"));
        assertThrows(IllegalStateException.class, () -> manager.require("ws-1"));
        assertNull(manager.remove("ws-1"));
    }

    private RealtimeAgentRuntime runtime(DeviceSession device) {
        return new RealtimeAgentRuntime(device, bindingService, agentService, new RealtimeModelRouter());
    }

    private void stubStart() {
        when(bindingService.requireAgentForRobot(11L, 33L, "xiaoyou")).thenReturn(agentRow());
        when(agentService.getResolvedConfig(11L, 101L)).thenReturn(agentConfig("AUTO"));
    }

    private static RealtimeClientEvent.SessionStartEvent startEvent() {
        return new RealtimeClientEvent.SessionStartEvent(
                "xiaoyou", null, new RealtimeAudioFormat("PCM_S16LE", 16000, 1));
    }

    private static DeviceSession deviceSession() {
        return new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
    }

    private static AiAgentDO agentRow() {
        AiAgentDO row = new AiAgentDO();
        row.setId(101L);
        row.setTenantId(11L);
        row.setCode("xiaoyou");
        row.setRealtimeMode("AUTO");
        return row;
    }

    private static AiAgentConfig agentConfig(String mode) {
        return new AiAgentConfig(101L, 11L, "xiaoyou", "system prompt",
                201L, 1, mode, 301L, 302L, 303L, 304L,
                "SESSION", false, false);
    }
}
