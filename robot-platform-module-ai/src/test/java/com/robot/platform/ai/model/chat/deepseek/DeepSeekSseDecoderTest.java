package com.robot.platform.ai.model.chat.deepseek;

import com.robot.platform.ai.model.client.event.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekSseDecoderTest {

    private final DeepSeekSseDecoder decoder = new DeepSeekSseDecoder();

    @Test
    void decodesTextToolCallUsageAndDoneFixture() throws Exception {
        List<ProviderEvent> events = new ArrayList<>();
        for (String line : fixture().split("\\R")) {
            events.addAll(decoder.decodeLine(line));
        }

        assertEquals(6, events.size());
        assertEquals("你", ((ProviderEvent.TextDelta) events.get(0)).text());
        assertEquals("好", ((ProviderEvent.TextDelta) events.get(1)).text());

        ProviderEvent.ToolCallDelta firstTool = (ProviderEvent.ToolCallDelta) events.get(2);
        assertEquals(0, firstTool.index());
        assertEquals("call-1", firstTool.id());
        assertEquals("inspect_home", firstTool.name());
        assertEquals("{\"room\":", firstTool.argumentsDelta());

        ProviderEvent.ToolCallDelta secondTool = (ProviderEvent.ToolCallDelta) events.get(3);
        assertEquals(0, secondTool.index());
        assertNull(secondTool.id());
        assertNull(secondTool.name());
        assertEquals("\"living\"}", secondTool.argumentsDelta());

        ProviderEvent.Usage usage = (ProviderEvent.Usage) events.get(4);
        assertEquals(12, usage.inputTokens());
        assertEquals(7, usage.outputTokens());

        assertInstanceOf(ProviderEvent.TextDone.class, events.get(5));
        assertEquals("", ((ProviderEvent.TextDone) events.get(5)).text());
        assertEquals(1, events.stream().filter(ProviderEvent.TextDone.class::isInstance).count());
    }

    @Test
    void ignoresCommentsAndBlankLinesAndRejectsMalformedData() {
        assertTrue(decoder.decodeLine("").isEmpty());
        assertTrue(decoder.decodeLine(": keepalive").isEmpty());
        assertTrue(decoder.decodeLine("event: message").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> decoder.decodeLine("data: {not-json}"));
    }

    private static String fixture() throws IOException {
        try (InputStream in = DeepSeekSseDecoderTest.class.getResourceAsStream("/ai/deepseek/chat-stream.sse")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
