package com.robot.platform.ai.digitalhuman.service;

import com.robot.platform.ai.agent.service.*;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.realtime.runtime.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalHumanDialogueFactoryTest {
    final AiDigitalHumanService humans = mock(AiDigitalHumanService.class);
    final AiAgentService agents = mock(AiAgentService.class);
    final ResolvedModelResolver models = mock(ResolvedModelResolver.class);
    final ModelClientRegistry clients = mock(ModelClientRegistry.class);
    final DigitalHumanDialogueFactory factory = new DigitalHumanDialogueFactory(humans, agents, models, clients, new RealtimeModelRouter());
    final AiDigitalHumanDO human = new AiDigitalHumanDO();
    @BeforeEach void setup() {
        human.setAgentId(9L); human.setStatus("ENABLED"); human.setVoiceId("Cherry");
        when(humans.get(1, 42)).thenReturn(human);
        AiAgentDO agent = new AiAgentDO(); agent.setStatus("ENABLED"); when(agents.get(1, 9)).thenReturn(agent);
    }
    void route(String mode) {
        when(agents.getResolvedConfig(1, 9)).thenReturn(new AiAgentConfig(9, 1, "test", "Be helpful", 2, 1, mode,
                11L, 12L, 13L, 14L, "{\"voice\":\"old\",\"language\":\"zh\"}", "NONE", false, false));
    }
    ResolvedModel model(long id, String type) {
        var result = new ResolvedModel(1, id, 1, "QWEN", type, "test", "https://example.test", "{}", "{}", "test");
        when(models.resolve(1, id)).thenReturn(result); return result;
    }
    @Test void nativeUsesConfiguredAgentAndHumanVoice() {
        route("NATIVE"); model(12, "REALTIME_S2S");
        RealtimeVoiceClient client = mock(RealtimeVoiceClient.class); when(clients.requireRealtimeVoice("QWEN")).thenReturn(client);
        factory.open(1, 42, (turn, generation, event) -> {});
        var resolved = ArgumentCaptor.forClass(ResolvedModel.class);
        verify(client).openTurnAware(resolved.capture(), any());
        assertTrue(resolved.getValue().voiceConfigJson().contains("Cherry"));
        assertTrue(resolved.getValue().voiceConfigJson().contains("zh"));
        assertTrue(resolved.getValue().realtimeInstructions().contains("Be helpful"));
    }
    @Test void cascadeCanOverrideTtsModelWhileRetainingAsrAndChat() {
        route("CASCADE"); model(11, "CHAT"); model(13, "ASR"); model(19, "TTS"); human.setVoiceModelId(19L);
        when(clients.requireAsr("QWEN")).thenReturn(mock(AsrClient.class));
        when(clients.requireChat("QWEN")).thenReturn(mock(ChatModelClient.class));
        when(clients.requireTts("QWEN")).thenReturn(mock(TtsClient.class));
        var session = factory.open(1, 42, (turn, generation, event) -> {});
        assertInstanceOf(CascadeRealtimePipeline.class, session); verify(models).resolve(1, 19); verify(models, never()).resolve(1, 14);
        session.close();
    }
    @Test void rejectsDisabledHumanAndIncorrectModelType() {
        human.setStatus("DISABLED"); assertThrows(RuntimeException.class, () -> factory.open(1, 42, (t, g, e) -> {}));
        human.setStatus("ENABLED"); route("NATIVE"); model(12, "CHAT");
        assertThrows(RuntimeException.class, () -> factory.open(1, 42, (t, g, e) -> {})); verifyNoInteractions(clients);
    }
}
