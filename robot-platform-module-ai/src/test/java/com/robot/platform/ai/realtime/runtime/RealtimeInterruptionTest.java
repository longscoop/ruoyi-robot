package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.ModelClientRegistry;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeTurnListener;
import com.robot.platform.ai.model.client.RealtimeVoiceClient;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeAudioFormat;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeInterruptionTest {

    @Test
    void dropsLateOldTurnAudioThroughProductionProviderListenerAndContinuesNewTurn() {
        for (int iteration = 0; iteration < 100; iteration++) {
            Scenario scenario = scenario(iteration);
            RealtimeAgentRuntime runtime = scenario.runtime();

            runtime.acceptControl(startEvent());
            runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("a-start"));
            runtime.acceptAudio(ByteBuffer.wrap(new byte[]{1}));
            runtime.acceptControl(new RealtimeClientEvent.SpeechStoppedEvent("a-stop"));

            TurnGeneration turnA = runtime.activeGenerationSnapshot();
            assertNotNull(turnA);
            scenario.provider().emitResponse(0, new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{10})));
            assertEquals(1, scenario.output().binary.size());

            runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("b-start"));
            TurnGeneration turnB = runtime.activeGenerationSnapshot();

            assertNotNull(turnB);
            assertNotEquals(turnA.generation(), turnB.generation());
            assertTrue(turnA.cancelled());
            assertEquals(1, scenario.provider().session.cancelCount);
            assertEquals(RealtimeAgentRuntime.State.USER_SPEAKING, runtime.state());
            assertEquals(1, scenario.output().count(RealtimeServerEvent.PlaybackStopEvent.class));
            assertEquals(1, scenario.output().count(RealtimeServerEvent.AssistantInterruptedEvent.class));

            scenario.provider().emitResponse(0,
                    new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{99})));
            assertEquals(1, scenario.output().binary.size());

            runtime.acceptAudio(ByteBuffer.wrap(new byte[]{2}));
            runtime.acceptControl(new RealtimeClientEvent.SpeechStoppedEvent("b-stop"));

            scenario.provider().emitResponse(0,
                    new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{98})));
            assertEquals(1, scenario.output().binary.size());

            scenario.provider().emitResponse(1,
                    new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{20})));
            scenario.provider().emitResponse(1, new ProviderEvent.AudioDone());

            assertEquals(2, scenario.output().binary.size());
            assertArrayEquals(new byte[]{20}, scenario.output().binary.get(1));
            assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
            assertEquals(1, scenario.output().count(RealtimeServerEvent.PlaybackStopEvent.class));
            assertEquals(1, scenario.output().count(RealtimeServerEvent.AssistantInterruptedEvent.class));

            RealtimeServerEvent.PlaybackStopEvent stop = scenario.output().first(
                    RealtimeServerEvent.PlaybackStopEvent.class);
            RealtimeServerEvent.AssistantInterruptedEvent interrupted = scenario.output().first(
                    RealtimeServerEvent.AssistantInterruptedEvent.class);
            assertEquals(turnA.turnId(), stop.turnId());
            assertEquals(turnA.turnId(), interrupted.turnId());
            assertTrue(scenario.output().events.stream()
                    .filter(RealtimeServerEvent.AssistantAudioDoneEvent.class::isInstance)
                    .map(RealtimeServerEvent.AssistantAudioDoneEvent.class::cast)
                    .allMatch(event -> event.turnId().equals(turnB.turnId())));
        }
    }

    @Test
    void normalReadyToSpeakingDoesNotEmitInterruptionEvents() {
        Scenario scenario = scenario(200);
        RealtimeAgentRuntime runtime = scenario.runtime();

        runtime.acceptControl(startEvent());
        runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("first"));

        assertEquals(0, scenario.output().count(RealtimeServerEvent.PlaybackStopEvent.class));
        assertEquals(0, scenario.output().count(RealtimeServerEvent.AssistantInterruptedEvent.class));
        assertEquals(0, scenario.provider().session.cancelCount);
    }

    private static Scenario scenario(int suffix) {
        AiAgentRobotBindingService bindingService = mock(AiAgentRobotBindingService.class);
        AiAgentService agentService = mock(AiAgentService.class);
        ResolvedModelResolver modelResolver = mock(ResolvedModelResolver.class);
        FakeRealtimeClient provider = new FakeRealtimeClient();
        ModelClientRegistry registry = new ModelClientRegistry(List.of(provider), List.of(), List.of(), List.of());
        CapturingOutput output = new CapturingOutput();

        when(bindingService.requireAgentForRobot(11L, 33L, "xiaoyou")).thenReturn(agentRow());
        when(agentService.getResolvedConfig(11L, 101L)).thenReturn(agentConfig());
        when(modelResolver.resolve(11L, 302L)).thenReturn(new ResolvedModel(
                11L, 302L, 401L, "QWEN", "REALTIME_S2S", "qwen-realtime",
                "wss://qwen.example/realtime", "{}", "{}", "secret"));

        RealtimeAgentRuntime runtime = new RealtimeAgentRuntime(
                "ws-interrupt-" + suffix, deviceSession(), bindingService, agentService,
                new RealtimeModelRouter(), modelResolver, registry);
        runtime.attachOutput(output);
        return new Scenario(runtime, provider, output);
    }

    private static RealtimeClientEvent.SessionStartEvent startEvent() {
        return new RealtimeClientEvent.SessionStartEvent(
                "xiaoyou", null, new RealtimeAudioFormat("PCM_S16LE", 16000, 1));
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
                null, "SESSION", false, false);
    }

    private static DeviceSession deviceSession() {
        return new DeviceSession(11L, 22L, 33L, "SN-001", 4, ApiAudience.DEVICE);
    }

    private record Scenario(RealtimeAgentRuntime runtime,
                            FakeRealtimeClient provider,
                            CapturingOutput output) {
    }

    private static final class FakeRealtimeClient implements RealtimeVoiceClient {
        private final FakeSession session = new FakeSession();
        private RealtimeTurnListener turnListener;

        @Override public String providerType() { return "QWEN"; }

        @Override
        public RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener) {
            throw new AssertionError("Runtime must use generation-aware provider listener");
        }

        @Override
        public RealtimeProviderSession openTurnAware(ResolvedModel model, RealtimeTurnListener listener) {
            this.turnListener = listener;
            session.turnListener = listener;
            return session;
        }

        void emitResponse(int index, ProviderEvent event) {
            FakeSession.TurnStamp turn = session.responses.get(index);
            turnListener.onEvent(turn.turnId(), turn.generation(), event);
        }
    }

    private static final class FakeSession implements RealtimeProviderSession {
        private final List<TurnStamp> responses = new ArrayList<>();
        private RealtimeTurnListener turnListener;
        private String turnId;
        private long generation;
        private int cancelCount;

        @Override
        public void beginTurn(String turnId, long generation) {
            this.turnId = turnId;
            this.generation = generation;
        }

        @Override public void appendAudio(ByteBuffer pcm) { }
        @Override public void speechStarted() { }

        @Override
        public void speechStopped() {
            responses.add(new TurnStamp(turnId, generation));
        }

        @Override public void cancelCurrentResponse() { cancelCount++; }
        @Override public void close() { }

        private record TurnStamp(String turnId, long generation) {
        }
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

        long count(Class<? extends RealtimeServerEvent> type) {
            return events.stream().filter(type::isInstance).count();
        }

        <T extends RealtimeServerEvent> T first(Class<T> type) {
            return events.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
        }
    }
}
