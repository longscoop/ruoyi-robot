package com.robot.platform.ai.memory;

import com.robot.platform.ai.memory.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemorySelectionTest {
    private MemorySnippet row(String type, String text, double score) { return new MemorySnippet(1,"ROBOT",type,text,null,score); }
    @Test void unrelatedHighImportanceLocalFactIsNotARelevanceScoreAndDuplicatesDisappear() {
        var rows = List.of(row("RELATION","用户的猫叫小黑",1), row("PREFERENCE","回答简短",1),row("PREFERENCE","回答简短。",1));
        assertEquals(List.of("回答简短"), MemorySelection.select("一加一等于几", rows, 3, false).stream().map(MemorySnippet::content).toList());
        assertEquals(2, MemorySelection.select("你记得我家的猫叫什么名字吗", rows, 3, false).size());
        assertEquals(1, MemorySelection.select("", rows, 3, false).size());
    }
    @Test void semanticScoresHaveAGateAndMissingScoresCannotForceInjection() {
        var rows = List.of(row("FACT","需要召回的语义事实",.9), row("FACT","弱相关事实",.3), row("FACT","缺少分数",0));
        assertEquals(1, MemorySelection.select("同义问题", rows, 3, true).size());
        assertTrue(MemorySelection.select("", rows, 3, true).isEmpty());
    }
}
