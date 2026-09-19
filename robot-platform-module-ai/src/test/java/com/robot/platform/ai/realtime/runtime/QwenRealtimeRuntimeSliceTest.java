package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeProtocolCodec;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QwenRealtimeRuntimeSliceTest {

    @Test
    void rk3588ProtocolFlowsThroughRuntimeAndQwenClientBackToPlatformEventsAndBinaryAudio() {
        AiAgentRobotBindingService bindingService = mock(AiAgentRobotBindingService.class);
        AiAgentService agentService = mock(AiAgentService.class);
        ResolvedModelResolver modelResolver = mock(ResolvedModelResolver.class);
        FakeQwenClient qwen = new FakeQwenClient();
        ModelClientRegistry registry = new ModelClientRegistry(List.of(qwen), List.of(), List.of(), List.of());
        CapturingOutput output = new CapturingOutput();

        when(bindingService.requireAgentForRobot(11L, 33L, "xiaoyou")).thenReturn(agentRow());
        when(agentService.getResolvedConfig(11L, 101L)).thenReturn(agentConfig());
        when(modelResolver.resolve(11L, 302L)).thenReturn(new ResolvedModel(
                11L, 302L, 401L, "QWEN", "REALTIME_S2S", "qwen-model-from-db",
                "wss://qwen.example/realtime", "{}", "{}", "decrypted-secret"));

        RealtimeAgentRuntime runtime = new RealtimeAgentRuntime(
                "ws-1", deviceSession(), bindingService, agentService,
                new RealtimeModelRouter(), modelResolver, registry);
        runtime.attachOutput(output);
        RealtimeProtocolCodec platformCodec = new RealtimeProtocolCodec();

        RealtimeClientEvent start = platformCodec.decodeClientText("""
                {"type":"session.start","agentCode":"xiaoyou",
                 "audio":{"codec":"PCM_S16LE","sampleRate":16000,"channels":1}}
                """);
        runtime.acceptControl(start);

        assertNotNull(qwen.openedModel);
        assertEquals("qwen-model-from-db", qwen.openedModel.modelCode());
        assertEquals("system prompt", qwen.openedModel.realtimeInstructions());
        assertEquals("{\"voice\":\"Cherry\"}", qwen.openedModel.voiceConfigJson());
        assertEquals("decrypted-secret", qwen.openedModel.credential());
        assertInstanceOf(RealtimeServerEvent.SessionCreatedEvent.class, output.events.get(0));

        runtime.acceptControl(platformCodec.decodeClientText(
                "{\"type\":\"input.speech_started\",\"eventId\":\"evt-1\"}"));
        runtime.acceptAudio(ByteBuffer.wrap(new byte[]{10, 20, 30}));
        runtime.acceptControl(platformCodec.decodeClientText(
                "{\"type\":\"input.speech_stopped\",\"eventId\":\"evt-2\"}"));

        assertEquals(1, qwen.session.speechStarted);
        assertEquals(1, qwen.session.speechStopped);
        assertArrayEquals(new byte[]{10, 20, 30}, qwen.session.audio.get(0));

        qwen.emit(new ProviderEvent.TranscriptDelta("你"));
        qwen.emit(new ProviderEvent.TranscriptDone("你好"));
        qwen.emit(new ProviderEvent.TextDelta("我"));
        qwen.emit(new ProviderEvent.TextDone("我在"));
        qwen.emit(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
        qwen.emit(new ProviderEvent.AudioDone());

        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.InputTranscriptDeltaEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.InputTranscriptDoneEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantTextDeltaEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantTextDoneEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantAudioStartedEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantAudioDoneEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantDoneEvent.class::isInstance));
        assertEquals(1, output.binary.size());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, output.binary.get(0));
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
    }

    private static AiAgentDO agentRow() {
        AiAgentDO row = new AiAgentDO();
        row.setId(101L);
        row.setTenantId(11L);
        row.setCode("xiaoyou");
        row.setRealtimeMode("NATIVE");
        return row;
    }

    private static AiAgentConfig agentConfig() {
        return new AiAgentConfig(101L, 11L, "xiaoyou", "system prompt",
                201L, 1, "NATIVE", null, 302L, null, null,
                "{\"voice\":\"Cherry\"}", "SESSION", false, false);
    }

    private static DeviceSession deviceSession() {
        return new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
    }

    private static final class FakeQwenClient implements RealtimeVoiceClient {
        private final FakeSession session = new FakeSession();
        private ResolvedModel openedModel;
        private RealtimeProviderListener listener;

        @Override
        public String providerType() {
            return "QWEN";
        }

        @Override
        public RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener) {
            this.openedModel = model;
            this.listener = listener;
            return session;
        }

        void emit(ProviderEvent event) {
            listener.onEvent(event);
        }
    }

    private static final class FakeSession implements RealtimeProviderSession {
        private int speechStarted;
        private int speechStopped;
        private final List<byte[]> audio = new ArrayList<>();

        @Override
        public void appendAudio(ByteBuffer pcm) {
            ByteBuffer copy = pcm.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            audio.add(bytes);
        }

        @Override public void speechStarted() { speechStarted++; }
        @Override public void speechStopped() { speechStopped++; }
        @Override public void cancelCurrentResponse() { }
        @Override public void close() { }
    }

    private static final class CapturingOutput implements RealtimeRuntimeOutput {
        private final List<RealtimeServerEvent> events = new ArrayList<>();
        private final List<byte[]> binary = new ArrayList<>();

        @Override
        public void sendEvent(RealtimeServerEvent event) {
            events.add(event);
        }

        @Override
        public void sendAudio(ByteBuffer audio) {
            ByteBuffer copy = audio.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            binary.add(bytes);
        }
    }
}
