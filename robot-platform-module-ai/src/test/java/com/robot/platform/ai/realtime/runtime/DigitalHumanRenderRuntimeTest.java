package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.*;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.digitalhuman.provider.*;
import com.robot.platform.ai.digitalhuman.realtime.DigitalHumanSessionResolver;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.ai.realtime.protocol.*;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.security.ApiAudience;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.ByteBuffer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalHumanRenderRuntimeTest {
    @Test void negotiatesThenRendersModelAudioAndStopsPlaybackEvenAfterGenerationHasFinished() {
        var bindings = mock(AiAgentRobotBindingService.class);
        var agents = mock(AiAgentService.class);
        var resolver = mock(ResolvedModelResolver.class);
        var registry = mock(ModelClientRegistry.class);
        var voice = mock(RealtimeVoiceClient.class);
        var voiceSession = mock(RealtimeProviderSession.class);
        var digitalHumans = mock(DigitalHumanSessionResolver.class);
        var renderers = mock(DigitalHumanProviders.class);
        var renderSession = mock(DigitalHumanProvider.Session.class);
        var output = mock(RealtimeRuntimeOutput.class);
        AiAgentDO agent = new AiAgentDO(); agent.setId(101L); agent.setCode("assistant");
        AiDigitalHumanDO human = new AiDigitalHumanDO(); human.setId(501L); human.setConfigJson("render-config");
        when(bindings.requireAgentForRobot(11L, 33L, "assistant")).thenReturn(agent);
        when(agents.getResolvedConfig(11L, 101L)).thenReturn(new AiAgentConfig(101L, 11L, "assistant", "prompt",
                201L, 1, "NATIVE", null, 302L, null, null, null, "SESSION", false, false));
        when(resolver.resolve(11L, 302L)).thenReturn(new ResolvedModel(11L, 302L, 401L, "QWEN", "REALTIME_S2S", "model", "wss://example", "{}", "{}", "secret"));
        when(registry.requireRealtimeVoice("QWEN")).thenReturn(voice);
        when(voice.openTurnAware(any(), any())).thenReturn(voiceSession);
        when(digitalHumans.resolve(11L, "human", "assistant")).thenReturn(new DigitalHumanSessionResolver.Resolved(human, agent));
        when(renderers.describe("render-config")).thenReturn(new DigitalHumanProviders.ClientConfig("LIVETALKING", List.of()));
        when(renderers.open("render-config", "v=0 offer")).thenReturn(renderSession);
        when(renderSession.answerSdp()).thenReturn("v=0 answer");
        var runtime = new RealtimeAgentRuntime("ws", new DeviceSession(11L, 22L, 33L, "SN", 1, ApiAudience.DEVICE), bindings, agents, new RealtimeModelRouter(), resolver, registry);
        runtime.attachOutput(output);
        runtime.attachDigitalHumanResolver(digitalHumans);
        runtime.attachRenderProviders(renderers);
        try {
            runtime.acceptControl(new RealtimeClientEvent.SessionStartEvent("assistant", null, new RealtimeAudioFormat("PCM_S16LE", 16000, 1), "human", null));
            ArgumentCaptor<RealtimeTurnListener> listener = ArgumentCaptor.forClass(RealtimeTurnListener.class);
            verify(voice).openTurnAware(any(), listener.capture());
            runtime.acceptControl(new RealtimeClientEvent.DigitalHumanOfferEvent("v=0 offer"));
            verify(output).sendEvent(new RealtimeServerEvent.DigitalHumanAnswerEvent("ws", "v=0 answer"));
            runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("start"));
            verify(renderSession, timeout(2000)).interrupt();
            runtime.acceptControl(new RealtimeClientEvent.SpeechStoppedEvent("stop"));
            var turn = runtime.activeGenerationSnapshot();
            listener.getValue().onEvent(turn.turnId(), turn.generation(), new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[] {1, 2})));
            listener.getValue().onEvent(turn.turnId(), turn.generation(), new ProviderEvent.AudioDone());
            verify(renderSession, timeout(2000)).audio(argThat(wav -> wav.length == 46 && wav[44] == 1));
            verify(output, never()).sendAudio(any());
            assertEquals(RealtimeAgentRuntime.State.READY, runtime.state());
            runtime.acceptControl(new RealtimeClientEvent.SpeechStartedEvent("next"));
            verify(renderSession, timeout(2000).times(2)).interrupt();
            listener.getValue().onEvent(turn.turnId(), turn.generation(), new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[24000])));
            runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "done"));
            verify(renderSession, timeout(2000)).close();
            verify(renderSession, times(1)).audio(any());
        } finally { runtime.close(new RealtimeAgentRuntime.CloseReason(1000, "test")); }
    }

    @Test void signalingCodecRejectsArbitraryUpstreamTargetsAndEncodesAnswer() {
        var codec = new RealtimeProtocolCodec();
        assertInstanceOf(RealtimeClientEvent.DigitalHumanOfferEvent.class, codec.decodeClientText("{\"type\":\"digital_human.offer\",\"sdp\":\"v=0\\r\\n\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeClientText("{\"type\":\"digital_human.offer\",\"sdp\":\"v=0\",\"url\":\"http://evil\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeClientText("{\"type\":\"digital_human.offer\",\"sdp\":\"invalid\"}"));
        assertTrue(codec.encodeServerEvent(new RealtimeServerEvent.DigitalHumanAnswerEvent("ws", "v=0")).contains("digital_human.answer"));
    }
}
