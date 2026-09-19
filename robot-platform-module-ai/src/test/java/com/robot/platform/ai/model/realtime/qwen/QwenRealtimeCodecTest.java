package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QwenRealtimeCodecTest {

    private final QwenRealtimeCodec codec = new QwenRealtimeCodec();

    @Test
    void buildsManualSessionUpdateFromAgentPromptAndVoiceConfig() {
        String json = codec.encodeSessionUpdate(
                "You are Xiaoyou.",
                "{\"voice\":\"Cherry\",\"temperature\":0.7}");

        var root = JsonUtils.parseTree(json);
        assertEquals("session.update", root.path("type").asText());
        assertEquals("You are Xiaoyou.", root.path("session").path("instructions").asText());
        assertEquals("Cherry", root.path("session").path("voice").asText());
        assertEquals(0.7, root.path("session").path("temperature").asDouble(), 0.0001);
        assertEquals("pcm", root.path("session").path("input_audio_format").asText());
        assertEquals("pcm", root.path("session").path("output_audio_format").asText());
        assertEquals("text", root.path("session").path("modalities").get(0).asText());
        assertEquals("audio", root.path("session").path("modalities").get(1).asText());
        assertTrue(root.path("session").has("turn_detection"));
        assertTrue(root.path("session").path("turn_detection").isNull());
    }

    @Test
    void encodesAudioAsBase64OnlyInsideQwenAdapter() {
        byte[] pcm = new byte[]{1, 2, 3, 4};

        String json = codec.encodeAudioAppend(ByteBuffer.wrap(pcm));

        var root = JsonUtils.parseTree(json);
        assertEquals("input_audio_buffer.append", root.path("type").asText());
        assertArrayEquals(pcm, Base64.getDecoder().decode(root.path("audio").asText()));
    }

    @Test
    void encodesManualTurnAndCancellationEvents() {
        assertEquals("input_audio_buffer.clear",
                JsonUtils.parseTree(codec.encodeSpeechStarted()).path("type").asText());
        assertEquals("input_audio_buffer.commit",
                JsonUtils.parseTree(codec.encodeAudioCommit()).path("type").asText());
        assertEquals("response.create",
                JsonUtils.parseTree(codec.encodeResponseCreate()).path("type").asText());
        assertEquals("response.cancel",
                JsonUtils.parseTree(codec.encodeResponseCancel()).path("type").asText());
    }

    @Test
    void normalizesTranscriptTextAudioToolUsageAndErrorFixtures() {
        assertEvent(codec.decodeServerEvent("""
                {"type":"conversation.item.input_audio_transcription.delta",
                 "text":"你好","stash":"，小哟"}
                """), ProviderEvent.TranscriptDelta.class, e ->
                assertEquals("你好，小哟", ((ProviderEvent.TranscriptDelta) e).text()));

        assertEvent(codec.decodeServerEvent("""
                {"type":"conversation.item.input_audio_transcription.completed",
                 "transcript":"你好，小哟"}
                """), ProviderEvent.TranscriptDone.class, e ->
                assertEquals("你好，小哟", ((ProviderEvent.TranscriptDone) e).text()));

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.text.delta","delta":"我在"}
                """), ProviderEvent.TextDelta.class, e ->
                assertEquals("我在", ((ProviderEvent.TextDelta) e).text()));

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.text.done","text":"我在这里"}
                """), ProviderEvent.TextDone.class, e ->
                assertEquals("我在这里", ((ProviderEvent.TextDone) e).text()));

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.audio_transcript.delta","delta":"马上"}
                """), ProviderEvent.TextDelta.class, e ->
                assertEquals("马上", ((ProviderEvent.TextDelta) e).text()));

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.audio_transcript.done","transcript":"马上过来"}
                """), ProviderEvent.TextDone.class, e ->
                assertEquals("马上过来", ((ProviderEvent.TextDone) e).text()));

        String audio = Base64.getEncoder().encodeToString("pcm".getBytes(StandardCharsets.UTF_8));
        assertEvent(codec.decodeServerEvent(
                "{\"type\":\"response.audio.delta\",\"delta\":\"" + audio + "\"}"),
                ProviderEvent.AudioDelta.class, e -> {
                    ByteBuffer bytes = ((ProviderEvent.AudioDelta) e).audio();
                    byte[] actual = new byte[bytes.remaining()];
                    bytes.get(actual);
                    assertArrayEquals("pcm".getBytes(StandardCharsets.UTF_8), actual);
                });

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.audio.done"}
                """), ProviderEvent.AudioDone.class, e -> { });

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.function_call_arguments.done",
                 "call_id":"call-1","name":"inspect_home",
                 "arguments":"{\\\"room\\\":\\\"living\\\"}"}
                """), ProviderEvent.ToolCall.class, e -> {
                    ProviderEvent.ToolCall call = (ProviderEvent.ToolCall) e;
                    assertEquals("call-1", call.id());
                    assertEquals("inspect_home", call.name());
                    assertEquals("{\"room\":\"living\"}", call.argumentsJson());
                });

        assertEvent(codec.decodeServerEvent("""
                {"type":"response.done","response":{"status":"completed",
                 "usage":{"input_tokens":12,"output_tokens":7}}}
                """), ProviderEvent.Usage.class, e -> {
                    ProviderEvent.Usage usage = (ProviderEvent.Usage) e;
                    assertEquals(12, usage.inputTokens());
                    assertEquals(7, usage.outputTokens());
                });

        assertEvent(codec.decodeServerEvent("""
                {"type":"error","error":{"type":"server_error",
                 "code":"overloaded","message":"try later"}}
                """), ProviderEvent.ProviderError.class, e -> {
                    ProviderEvent.ProviderError error = (ProviderEvent.ProviderError) e;
                    assertEquals("overloaded", error.code());
                    assertEquals("try later", error.message());
                    assertTrue(error.retryable());
                });
    }

    @Test
    void ignoresNonNormalizedLifecycleEvents() {
        assertTrue(codec.decodeServerEvent("""
                {"type":"session.created","session":{"id":"sess-1"}}
                """).isEmpty());
        assertTrue(codec.decodeServerEvent("""
                {"type":"response.created","response":{"id":"resp-1"}}
                """).isEmpty());
    }

    private static void assertEvent(List<ProviderEvent> events,
                                    Class<? extends ProviderEvent> type,
                                    java.util.function.Consumer<ProviderEvent> assertion) {
        assertEquals(1, events.size());
        assertInstanceOf(type, events.get(0));
        assertion.accept(events.get(0));
    }
}
