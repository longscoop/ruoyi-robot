package com.robot.platform.ai.memory;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.memory.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MySqlMemoryRetrieverTest {

    @Test
    void appliesTenantMemberRobotAndExpiryBoundaryBeforeRanking() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 20, 12, 0);
        when(mapper.selectActiveCandidates(11L, 33L, 99L, now))
                .thenReturn(List.of(memory(1L, "MEMBER", "PREFERENCE", "coffee less sugar", .8, now.minusDays(1))));
        MySqlMemoryRetriever retriever = new MySqlMemoryRetriever(mapper);

        List<MemorySnippet> result = retriever.retrieve(
                new MemoryQuery(11L, 33L, 99L, "coffee sugar", "PREFERENCE", now), 8);

        assertEquals(1, result.size());
        verify(mapper).selectActiveCandidates(11L, 33L, 99L, now);
    }

    @Test
    void anonymousQueryPassesNullMemberSoOnlyCurrentRobotCanBeSelectedBySql() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 20, 12, 0);
        when(mapper.selectActiveCandidates(11L, 33L, null, now)).thenReturn(List.of());
        new MySqlMemoryRetriever(mapper).retrieve(
                new MemoryQuery(11L, 33L, null, "home", null, now), 8);
        verify(mapper).selectActiveCandidates(11L, 33L, null, now);
    }

    @Test
    void deterministicRankingUsesTextTypeImportanceAndRecencyAndDefaultsToEight() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 20, 12, 0);
        AiMemoryDO best = memory(2L, "MEMBER", "PREFERENCE", "coffee less sugar", .9, now.minusDays(1));
        AiMemoryDO weaker = memory(1L, "MEMBER", "FACT", "likes tea", .5, now.minusDays(60));
        when(mapper.selectActiveCandidates(11L, 33L, 99L, now)).thenReturn(List.of(weaker, best));

        List<MemorySnippet> result = new MySqlMemoryRetriever(mapper).retrieve(
                new MemoryQuery(11L, 33L, 99L, "coffee sugar", "PREFERENCE", now));

        assertEquals(2L, result.get(0).id());
        assertEquals(1, result.size()); // Unrelated memories must not be injected merely for being recent.
    }

    @Test
    void chineseRecallExcludesUnrelatedPetButKeepsRelevantAndStandingPreferences() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectActiveCandidates(11L, 33L, null, now)).thenReturn(List.of(
                memory(1, "ROBOT", "RELATION", "用户养了一只猫，名字叫小黑", .9, now),
                memory(2, "ROBOT", "WORK", "用户在开发机器人项目", .8, now),
                memory(3, "ROBOT", "PREFERENCE", "用户希望回答简短", .9, now)));
        var retriever = new MySqlMemoryRetriever(mapper);
        assertEquals(3, retriever.retrieveBackground(new MemoryQuery(11,33,null,"",null,now),8).size());
        assertEquals(List.of(3L), retriever.retrieve(new MemoryQuery(11,33,null,"",null,now),8)
                .stream().map(MemorySnippet::id).toList());
        var pet = retriever.retrieve(new MemoryQuery(11,33,null,"你还记得我家里的猫叫什么吗",null,now),8);
        assertTrue(pet.stream().anyMatch(m -> m.id() == 1));
        assertFalse(pet.stream().anyMatch(m -> m.id() == 2));
        var work = retriever.retrieve(new MemoryQuery(11,33,null,"机器人项目怎么样",null,now),8);
        assertTrue(work.stream().anyMatch(m -> m.id() == 2));
        assertFalse(work.stream().anyMatch(m -> m.id() == 1));
    }

    private static AiMemoryDO memory(long id, String scope, String type, String content,
                                     double importance, LocalDateTime observedAt) {
        AiMemoryDO row = new AiMemoryDO();
        row.setId(id); row.setScope(scope); row.setMemoryType(type); row.setContent(content);
        row.setImportance(BigDecimal.valueOf(importance)); row.setLastObservedAt(observedAt);
        return row;
    }
}
