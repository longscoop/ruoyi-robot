package com.robot.platform.ai.memory.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryDirectiveParserTest {
    private final MemoryDirectiveParser parser = new MemoryDirectiveParser();

    @Test void recognizesChineseDirectives() {
        assertEquals(MemoryDirectiveParser.Directive.REMEMBER, parser.parse("记住，我喝咖啡少糖"));
        assertEquals(MemoryDirectiveParser.Directive.DO_NOT_REMEMBER, parser.parse("别记这个"));
        assertEquals(MemoryDirectiveParser.Directive.FORGET, parser.parse("忘掉我刚才说的咖啡偏好"));
        assertEquals(MemoryDirectiveParser.Directive.NORMAL, parser.parse("讲个笑话"));
    }

    @Test void explicitNegativeDirectiveWinsOverRememberText() {
        assertEquals(MemoryDirectiveParser.Directive.DO_NOT_REMEMBER, parser.parse("别记这个，也不用记住"));
    }

    @Test void recognizesSimpleEnglishDirectives() {
        assertEquals(MemoryDirectiveParser.Directive.REMEMBER, parser.parse("Remember that I take coffee without sugar"));
        assertEquals(MemoryDirectiveParser.Directive.DO_NOT_REMEMBER, parser.parse("Don't remember this"));
        assertEquals(MemoryDirectiveParser.Directive.FORGET, parser.parse("Forget my coffee preference"));
    }
}
