package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.realtime.service.RealtimeTraceService;
import com.robot.platform.ai.memory.pipeline.MemoryPipeline;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeProtocolCodec;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.digitalhuman.realtime.DigitalHumanSessionResolver;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QwenRealtimeRuntimeSliceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rk3588ProtocolFlowsThroughRuntimeAndQwenClientBackToPlatformEventsAndBinaryAudio(boolean digitalHuman) {
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
        RealtimeTraceService traces = mock(RealtimeTraceService.class);
        var trace = new RealtimeTraceService.Trace(11, 501, 601, 302L);
        when(traces.start(any(), any(), any())).thenAnswer(invocation -> {
            assertEquals(11L, TenantContextHolder.getRequiredTenantId());
            return trace;
        });
        doAnswer(invocation -> {
            assertEquals(11L, TenantContextHolder.getRequiredTenantId());
            return null;
        }).when(traces).message(any(), anyString(), anyString(), anyString());
        runtime.attachTraceService(traces);
        if (digitalHuman) {
            var resolver = mock(DigitalHumanSessionResolver.class);
            var avatar = new AiDigitalHumanDO(); avatar.setId(8L); avatar.setCode("avatar"); avatar.setAgentId(101L); avatar.setVoiceId("Serena");
            when(resolver.resolve(11, "avatar", "xiaoyou")).thenReturn(new DigitalHumanSessionResolver.Resolved(avatar, agentRow()));
            runtime.attachDigitalHumanResolver(resolver);
        }
        runtime.attachOutput(output);
        RealtimeProtocolCodec platformCodec = new RealtimeProtocolCodec();

        RealtimeClientEvent start = platformCodec.decodeClientText("""
                {"type":"session.start","agentCode":"xiaoyou",
                 "audio":{"codec":"PCM_S16LE","sampleRate":16000,"channels":1}}
                """);
        if (digitalHuman) start = platformCodec.decodeClientText("{\"type\":\"session.start\",\"agentCode\":\"xiaoyou\",\"digitalHumanCode\":\"avatar\",\"audio\":{\"codec\":\"PCM_S16LE\",\"sampleRate\":16000,\"channels\":1}}");
        runtime.acceptControl(start);

        assertNotNull(qwen.openedModel);
        assertEquals("qwen-model-from-db", qwen.openedModel.modelCode());
        assertTrue(qwen.openedModel.realtimeInstructions().startsWith("system prompt"));
        assertTrue(qwen.openedModel.realtimeInstructions().contains("voice_interaction_rules"));
        assertEquals(digitalHuman ? "{\"voice\":\"Serena\"}" : "{\"voice\":\"Cherry\"}", qwen.openedModel.voiceConfigJson());
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
        // Providers can redeliver final events; each role is persisted once.
        qwen.emit(new ProviderEvent.TranscriptDone("你好"));
        qwen.emit(new ProviderEvent.TextDone("我在"));
        qwen.emit(new ProviderEvent.AudioDone());
        verify(traces).message(trace, "turn-1", "USER", "你好");
        verify(traces).message(trace, "turn-1", "ASSISTANT", "我在");
        // Empty ASR is a skipped turn; stale provider output cannot leak into playback or persistence.
        runtime.acceptControl(platformCodec.decodeClientText("{\"type\":\"input.speech_started\"}"));
        runtime.acceptControl(platformCodec.decodeClientText("{\"type\":\"input.speech_stopped\"}"));
        qwen.emit(new ProviderEvent.TranscriptDone(" ，。 "));
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
        qwen.emit(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{9})));
        qwen.emit(new ProviderEvent.TextDone("noise reply"));
        assertEquals(1, output.binary.size());
        verify(traces, never()).message(trace, "turn-2", "USER", " ，。 ");
        verify(traces, never()).message(trace, "turn-2", "ASSISTANT", "noise reply");
        runtime.acceptControl(platformCodec.decodeClientText("{\"type\":\"input.speech_started\"}"));
        runtime.acceptControl(platformCodec.decodeClientText("{\"type\":\"input.speech_stopped\"}"));
        qwen.emit(new ProviderEvent.TranscriptDone("继续"));
        qwen.emit(new ProviderEvent.TextDone("好"));
        qwen.emit(new ProviderEvent.AudioDone());
        assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
        verify(traces).message(trace, "turn-3", "USER", "继续");
        runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "CLIENT_CLOSE"));
        runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "CLIENT_CLOSE"));
        verify(traces).finish(eq(trace), isNull(), notNull(), notNull(), eq(0));
        assertNull(TenantContextHolder.getTenantId());
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void memoryBatchesThreeCompletedTurnsAndFlushesRemainderOnClose(boolean forget) {
        var bindings=mock(AiAgentRobotBindingService.class);var agents=mock(AiAgentService.class);var models=mock(ResolvedModelResolver.class);
        var qwen=new FakeQwenClient();var clients=new ModelClientRegistry(List.of(qwen),List.of(),List.of(),List.of());
        var pipeline=mock(MemoryPipeline.class);when(pipeline.messageThreshold(any())).thenReturn(forget ? 100 : 6);
        when(bindings.requireAgentForRobot(11,33,"xiaoyou")).thenReturn(agentRow());
        var config=new AiAgentConfig(101,11,"xiaoyou","角色",201,1,"NATIVE",301L,302L,null,null,"{\"voice\":\"Cherry\"}","MEM_LOCAL_SHORT",true,true);
        when(agents.getResolvedConfig(11,101)).thenReturn(config);
        when(models.resolve(11,302)).thenReturn(new ResolvedModel(11,302,401,"QWEN","REALTIME_S2S","model","wss://test","{}","{}","test"));
        var runtime=new RealtimeAgentRuntime("batch",deviceSession(),bindings,agents,new RealtimeModelRouter(),models,clients,null,pipeline,null);
        runtime.attachOutput(new CapturingOutput());var codec=new RealtimeProtocolCodec();
        runtime.acceptControl(codec.decodeClientText("{\"type\":\"session.start\",\"agentCode\":\"xiaoyou\",\"audio\":{\"codec\":\"PCM_S16LE\",\"sampleRate\":16000,\"channels\":1}}"));
        for(int n=0;n<4;n++) {
            runtime.acceptControl(codec.decodeClientText("{\"type\":\"input.speech_started\"}"));
            runtime.acceptControl(codec.decodeClientText("{\"type\":\"input.speech_stopped\"}"));
            qwen.emit(new ProviderEvent.TranscriptDone(forget && n==3 ? "忘记用户事实0" : "用户事实"+n));qwen.emit(new ProviderEvent.TextDone("回答"+n));qwen.emit(new ProviderEvent.AudioDone());qwen.emit(new ProviderEvent.AudioDone());
            if(n<2)verify(pipeline,never()).submitBatch(anyList(),any(),any());
        }
        verify(pipeline).submitBatch(argThat(batch->batch.size()==3),any(),eq(config));
        runtime.close(new RealtimeAgentRuntime.CloseReason(1000,"USER_GOODBYE"));
        runtime.close(new RealtimeAgentRuntime.CloseReason(1000,"duplicate"));
        verify(pipeline).submitBatch(argThat(batch->batch.size()==1&&batch.get(0).userText().equals(forget ? "忘记用户事实0" : "用户事实3")),any(),eq(config));
        verify(pipeline,times(2)).submitBatch(anyList(),any(),any());
        var order=inOrder(pipeline);
        order.verify(pipeline).submitBatch(argThat(batch->batch.size()==3),any(),eq(config));
        order.verify(pipeline).submitBatch(argThat(batch->batch.size()==1),any(),eq(config));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"你退出吧。", "你退一下。", "小志，你退下，不要再说话了。"})
    void exitTranscriptClosesRuntimeAndSuppressesLateProviderOutput(String text) {
        var bindings=mock(AiAgentRobotBindingService.class);var agents=mock(AiAgentService.class);var models=mock(ResolvedModelResolver.class);
        var qwen=new FakeQwenClient();var clients=new ModelClientRegistry(List.of(qwen),List.of(),List.of(),List.of());
        when(bindings.requireAgentForRobot(11,33,"xiaoyou")).thenReturn(agentRow());
        when(agents.getResolvedConfig(11,101)).thenReturn(agentConfig());
        when(models.resolve(11,302)).thenReturn(new ResolvedModel(11,302,401,"QWEN","REALTIME_S2S","model","wss://test","{}","{}","test"));
        var runtime=new RealtimeAgentRuntime("exit",deviceSession(),bindings,agents,new RealtimeModelRouter(),models,clients);
        var output=new CapturingOutput();runtime.attachOutput(output);var codec=new RealtimeProtocolCodec();
        runtime.acceptControl(codec.decodeClientText("{\"type\":\"session.start\",\"agentCode\":\"xiaoyou\",\"audio\":{\"codec\":\"PCM_S16LE\",\"sampleRate\":16000,\"channels\":1}}"));
        runtime.acceptControl(codec.decodeClientText("{\"type\":\"input.speech_started\"}"));
        runtime.acceptControl(codec.decodeClientText("{\"type\":\"input.speech_stopped\"}"));
        qwen.emit(new ProviderEvent.TranscriptDone(text));
        assertEquals(RealtimeAgentRuntime.State.CLOSED,runtime.state());
        assertTrue(output.events.stream().anyMatch(e->e instanceof RealtimeServerEvent.SessionClosedEvent c && "USER_GOODBYE".equals(c.reason())));
        qwen.emit(new ProviderEvent.TextDone("我退出啦，小黑陪您"));
        qwen.emit(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1,2})));
        assertTrue(output.binary.isEmpty());
        var replies=output.events.stream().filter(RealtimeServerEvent.AssistantTextDoneEvent.class::isInstance)
                .map(RealtimeServerEvent.AssistantTextDoneEvent.class::cast).toList();
        assertEquals(1,replies.size());assertEquals("好的，再见。",replies.get(0).text());
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
