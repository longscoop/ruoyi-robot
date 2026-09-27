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
        assertTrue(result.get(0).score() > result.get(1).score());
    }

    private static AiMemoryDO memory(long id, String scope, String type, String content,
                                     double importance, LocalDateTime observedAt) {
        AiMemoryDO row = new AiMemoryDO();
        row.setId(id); row.setScope(scope); row.setMemoryType(type); row.setContent(content);
        row.setImportance(BigDecimal.valueOf(importance)); row.setLastObservedAt(observedAt);
        return row;
    }
}
