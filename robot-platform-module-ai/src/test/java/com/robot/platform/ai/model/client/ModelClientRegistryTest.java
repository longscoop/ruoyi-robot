package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelClientRegistryTest {

    @Test
    void selectsClientsByProviderTypeAndModelType() {
        RealtimeVoiceClient qwenRealtime = realtime("QWEN");
        RealtimeVoiceClient doubaoRealtime = realtime("DOUBAO");
        ChatModelClient deepseekChat = chat("DEEPSEEK");
        AsrClient qwenAsr = asr("QWEN");
        TtsClient qwenTts = tts("QWEN");

        ModelClientRegistry registry = new ModelClientRegistry(
                List.of(qwenRealtime, doubaoRealtime),
                List.of(deepseekChat),
                List.of(qwenAsr),
                List.of(qwenTts));

        assertSame(qwenRealtime, registry.require("QWEN", "REALTIME_S2S"));
        assertSame(doubaoRealtime, registry.require("doubao", "realtime_s2s"));
        assertSame(deepseekChat, registry.require("DEEPSEEK", "CHAT"));
        assertSame(qwenAsr, registry.require("QWEN", "ASR"));
        assertSame(qwenTts, registry.require("QWEN", "TTS"));

        assertSame(qwenRealtime, registry.requireRealtimeVoice("qwen"));
        assertSame(deepseekChat, registry.requireChat("deepseek"));
        assertSame(qwenAsr, registry.requireAsr("qwen"));
        assertSame(qwenTts, registry.requireTts("qwen"));
    }

    @Test
    void rejectsDuplicateProviderAndModelTypeRegistration() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ModelClientRegistry(
                        List.of(realtime("QWEN"), realtime("qwen")),
                        List.of(), List.of(), List.of()));

        assertTrue(error.getMessage().contains("QWEN"));
        assertTrue(error.getMessage().contains("REALTIME_S2S"));
    }

    @Test
    void failsClosedWhenNoMatchingClientExists() {
        ModelClientRegistry registry = new ModelClientRegistry(
                List.of(realtime("QWEN")), List.of(), List.of(), List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.require("DEEPSEEK", "CHAT"));

        assertTrue(error.getMessage().contains("DEEPSEEK"));
        assertTrue(error.getMessage().contains("CHAT"));
    }

    @Test
    void providerEventsAreVendorNeutralAndAudioRemainsBinary() {
        List<ProviderEvent> events = List.of(
                new ProviderEvent.TranscriptDelta("你"),
                new ProviderEvent.TranscriptDone("你好"),
                new ProviderEvent.TextDelta("你"),
                new ProviderEvent.TextDone("你好"),
                new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1, 2, 3})),
                new ProviderEvent.AudioDone(),
                new ProviderEvent.ToolCall("call-1", "inspect_home", "{\"room\":\"living\"}"),
                new ProviderEvent.Usage(12, 34),
                new ProviderEvent.ProviderError("RATE_LIMIT", "slow down", true)
        );

        assertEquals(9, events.size());
        ProviderEvent.AudioDelta audio = (ProviderEvent.AudioDelta) events.get(4);
        byte[] bytes = new byte[audio.audio().remaining()];
        audio.audio().duplicate().get(bytes);
        assertArrayEquals(new byte[]{1, 2, 3}, bytes);

        String typeNames = events.stream().map(e -> e.getClass().getSimpleName()).reduce("", String::concat);
        assertFalse(typeNames.toLowerCase().contains("qwen"));
        assertFalse(typeNames.toLowerCase().contains("doubao"));
        assertFalse(typeNames.toLowerCase().contains("deepseek"));
    }

    @Test
    void resolvedModelToStringNeverLeaksCredential() {
        ResolvedModel model = new ResolvedModel(
                11L, 101L, 201L, "QWEN", "REALTIME_S2S",
                "qwen3.5-omni-plus-realtime", "wss://example.test/realtime",
                "{\"workspace\":\"default\"}", "{\"voice\":\"Cherry\"}", "secret-key");

        assertEquals("secret-key", model.credential());
        assertFalse(model.toString().contains("secret-key"));
        assertTrue(model.toString().contains("[REDACTED]"));
    }

    private static RealtimeVoiceClient realtime(String providerType) {
        return new RealtimeVoiceClient() {
            @Override public String providerType() { return providerType; }
            @Override public RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ChatModelClient chat(String providerType) {
        return new ChatModelClient() {
            @Override public String providerType() { return providerType; }
            @Override public ChatStream stream(ChatRequest request, ChatListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AsrClient asr(String providerType) {
        return new AsrClient() {
            @Override public String providerType() { return providerType; }
            @Override public AsrSession open(ResolvedModel model, AsrListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TtsClient tts(String providerType) {
        return new TtsClient() {
            @Override public String providerType() { return providerType; }
            @Override public TtsStream stream(TtsRequest request, TtsListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
