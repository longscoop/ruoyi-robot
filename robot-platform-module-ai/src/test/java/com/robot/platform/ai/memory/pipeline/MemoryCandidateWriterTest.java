package com.robot.platform.ai.memory.pipeline;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.memory.extract.*;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.*;
import com.robot.platform.ai.memory.service.MemoryStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryCandidateWriterTest {
    @Test void duplicateReplacesOnlyTheSameIdentityScopeAndRejectsAnonymousMemberMemory() {
        var mapper = mock(AiMemoryMapper.class);
        var store = mock(MemoryStore.class);
        var old = new AiMemoryDO(); old.setId(5L); old.setScope("ROBOT"); old.setMemoryType("FACT");
        old.setContent("充电桩在客厅"); old.setStatus("ACTIVE");
        when(mapper.selectActiveCandidates(eq(1L), eq(2L), isNull(), any())).thenReturn(List.of(old));
        when(store.save(any())).thenReturn(6L);
        var writer = new MemoryCandidateWriter(mapper, new DefaultMemoryPolicy(.75, .6), store);
        writer.write(List.of(new MemoryCandidate("ROBOT", "FACT", "充电桩在客厅", .9, .95, null),
                        new MemoryCandidate("MEMBER", "FACT", "未经验证的个人信息", .9, .95, null)),
                ConversationIdentity.anonymous(1, 2), MemoryDirectiveParser.Directive.REMEMBER,
                new MemoryExtractor.CompletedTurn("记住充电桩在客厅", "好的", 77L));
        verify(store).save(argThat(w -> w.tenantId() == 1 && w.memberId() == null
                && w.robotId() == 2 && w.sourceConversationId() == 77 && w.content().equals("充电桩在客厅")));
        verify(store).supersede(1L, 5L, 6L);
        verifyNoMoreInteractions(store);
    }

    @Test void forgetDeletesOnlyExactAuthorizedTarget() {
        var mapper = mock(AiMemoryMapper.class);
        var store = mock(MemoryStore.class);
        var target = new AiMemoryDO(); target.setId(1L); target.setScope("ROBOT"); target.setContent("演示口令是蓝色小船");
        var other = new AiMemoryDO(); other.setId(2L); other.setScope("ROBOT"); other.setContent("充电桩在客厅");
        when(mapper.selectActiveCandidates(eq(1L), eq(2L), isNull(), any())).thenReturn(List.of(target, other));
        new MemoryCandidateWriter(mapper, new DefaultMemoryPolicy(.75, .6), store).write(
                List.of(new MemoryCandidate("ROBOT", "FACT", "演示口令是蓝色小船", .9, .95, null)),
                ConversationIdentity.anonymous(1, 2), MemoryDirectiveParser.Directive.FORGET,
                new MemoryExtractor.CompletedTurn("忘记演示口令是蓝色小船", "好的"));
        verify(store).delete(1L, 1L);
        verifyNoMoreInteractions(store);
    }
}
