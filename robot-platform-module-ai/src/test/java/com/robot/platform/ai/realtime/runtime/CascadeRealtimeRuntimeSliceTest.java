package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
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

class CascadeRealtimeRuntimeSliceTest {

    @Test
    void cascadeRouteResolvesConfiguredModelsAndStreamsAudioBackToDevice() {
        AiAgentRobotBindingService bindingService = mock(AiAgentRobotBindingService.class);
        AiAgentService agentService = mock(AiAgentService.class);
        ResolvedModelResolver modelResolver = mock(ResolvedModelResolver.class);
        FakeAsrClient asr = new FakeAsrClient();
        FakeChatClient chat = new FakeChatClient();
        FakeTtsClient tts = new FakeTtsClient();
        ModelClientRegistry registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        CapturingOutput output = new CapturingOutput();

        when(bindingService.requireAgentForRobot(11L, 33L, "xiaoyou")).thenReturn(agentRow());
        when(agentService.getResolvedConfig(11L, 101L)).thenReturn(agentConfig());
        when(modelResolver.resolve(11L, 301L)).thenReturn(chatModel());
        when(modelResolver.resolve(11L, 303L)).thenReturn(asrModel());
        when(modelResolver.resolve(11L, 304L)).thenReturn(ttsModel());

        RealtimeAgentRuntime runtime = new RealtimeAgentRuntime(
                "ws-cascade", deviceSession(), bindingService, agentService,
                new RealtimeModelRouter(), modelResolver, registry);
        runtime.attachOutput(output);
        RealtimeProtocolCodec codec = new RealtimeProtocolCodec();

        runtime.acceptControl(codec.decodeClientText("""
                {"type":"session.start","agentCode":"xiaoyou",
                 "audio":{"codec":"PCM_S16LE","sampleRate":16000,"channels":1}}
                """));

        assertEquals(RealtimeRoute.Mode.CASCADE, runtime.route().mode());
        assertTrue(output.events.get(0) instanceof RealtimeServerEvent.SessionCreatedEvent);
        verify(modelResolver).resolve(11L, 301L);
        verify(modelResolver).resolve(11L, 303L);
        verify(modelResolver).resolve(11L, 304L);

        runtime.acceptControl(codec.decodeClientText(
                "{\"type\":\"input.speech_started\",\"eventId\":\"evt-1\"}"));
        runtime.acceptAudio(ByteBuffer.wrap(new byte[]{10, 20, 30}));
        runtime.acceptControl(codec.decodeClientText(
                "{\"type\":\"input.speech_stopped\",\"eventId\":\"evt-2\"}"));

        assertEquals(1, asr.session.speechStarted);
        assertEquals(1, asr.session.speechStopped);
        assertArrayEquals(new byte[]{10, 20, 30}, asr.session.audio.get(0));

        asr.emit(new ProviderEvent.TranscriptDone("你好"));
        assertEquals(1, chat.requests.size());
        assertEquals("system prompt", chat.requests.get(0).messages().get(0).content());
        assertEquals("你好", chat.requests.get(0).messages().get(1).content());

        chat.emit(new ProviderEvent.TextDelta("我在。"));
        assertEquals(List.of("我在。"), tts.texts);
        chat.emit(new ProviderEvent.TextDone(""));
        tts.emit(0, new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1, 2, 3})));
        tts.emit(0, new ProviderEvent.AudioDone());

        assertEquals(1, output.binary.size());
        assertArrayEquals(new byte[]{1, 2, 3}, output.binary.get(0));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.InputTranscriptDoneEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantTextDeltaEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantAudioDoneEvent.class::isInstance));
        assertTrue(output.events.stream().anyMatch(RealtimeServerEvent.AssistantDoneEvent.class::isInstance));
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
    }

    private static AiAgentDO agentRow() {
        AiAgentDO row = new AiAgentDO();
        row.setId(101L);
        row.setTenantId(11L);
        row.setCode("xiaoyou");
        row.setRealtimeMode("CASCADE");
        return row;
    }

    private static AiAgentConfig agentConfig() {
        return new AiAgentConfig(101L, 11L, "xiaoyou", "system prompt",
                201L, 1, "CASCADE", 301L, null, 303L, 304L,
                null, "SESSION", false, false);
    }

    private static ResolvedModel asrModel() {
        return new ResolvedModel(11L, 303L, 403L,
                "QWEN", "ASR", "fun-asr-realtime",
                "wss://asr.example", "{}", "{}", "asr-secret");
    }

    private static ResolvedModel chatModel() {
        return new ResolvedModel(11L, 301L, 401L,
                "DEEPSEEK", "CHAT", "deepseek-chat",
                "https://chat.example", "{}", "{}", "chat-secret");
    }

    private static ResolvedModel ttsModel() {
        return new ResolvedModel(11L, 304L, 404L,
                "QWEN", "TTS", "qwen-tts",
                "wss://tts.example", "{}", "{}", "tts-secret");
    }

    private static DeviceSession deviceSession() {
        return new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
    }

    private static final class FakeAsrClient implements AsrClient {
        private final FakeAsrSession session = new FakeAsrSession();
        private AsrListener listener;

        @Override public String providerType() { return "QWEN"; }
        @Override public AsrSession open(ResolvedModel model, AsrListener listener) {
            this.listener = listener;
            return session;
        }
        void emit(ProviderEvent event) { listener.onEvent(event); }
    }

    private static final class FakeAsrSession implements AsrSession {
        private int speechStarted;
        private int speechStopped;
        private final List<byte[]> audio = new ArrayList<>();

        @Override public void appendAudio(ByteBuffer pcm) {
            ByteBuffer copy = pcm.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            audio.add(bytes);
        }
        @Override public void speechStarted() { speechStarted++; }
        @Override public void speechStopped() { speechStopped++; }
        @Override public void cancel() { }
    }

    private static final class FakeChatClient implements ChatModelClient {
        private final List<ChatRequest> requests = new ArrayList<>();
        private ChatListener listener;

        @Override public String providerType() { return "DEEPSEEK"; }
        @Override public ChatStream stream(ChatRequest request, ChatListener listener) {
            requests.add(request);
            this.listener = listener;
            return () -> { };
        }
        void emit(ProviderEvent event) { listener.onEvent(event); }
    }

    private static final class FakeTtsClient implements TtsClient {
        private final List<String> texts = new ArrayList<>();
        private final List<TtsListener> listeners = new ArrayList<>();

        @Override public String providerType() { return "QWEN"; }
        @Override public TtsStream stream(TtsRequest request, TtsListener listener) {
            texts.add(request.text());
            listeners.add(listener);
            return () -> { };
        }
        void emit(int index, ProviderEvent event) { listeners.get(index).onEvent(event); }
    }

    private static final class CapturingOutput implements RealtimeRuntimeOutput {
        private final List<RealtimeServerEvent> events = new ArrayList<>();
        private final List<byte[]> binary = new ArrayList<>();

        @Override public void sendEvent(RealtimeServerEvent event) { events.add(event); }
        @Override public void sendAudio(ByteBuffer audio) {
            ByteBuffer copy = audio.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            binary.add(bytes);
        }
    }
}
