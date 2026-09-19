package com.robot.platform.ai.conversation;

import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;
import com.robot.platform.ai.conversation.dal.dataobject.AiConversationMessageDO;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMapper;
import com.robot.platform.ai.conversation.dal.mysql.AiConversationMessageMapper;
import com.robot.platform.ai.conversation.service.AiConversationService;
import com.robot.platform.ai.conversation.service.AiConversationServiceImpl;
import com.robot.platform.ai.realtime.dal.dataobject.AiRealtimeSessionDO;
import com.robot.platform.ai.realtime.dal.mysql.AiRealtimeSessionMapper;
import com.robot.platform.ai.realtime.service.AiRealtimeSessionService;
import com.robot.platform.ai.realtime.service.AiRealtimeSessionServiceImpl;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    @Mock private AiConversationMapper conversationMapper;
    @Mock private AiConversationMessageMapper messageMapper;
    @Mock private AiRealtimeSessionMapper realtimeSessionMapper;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void startsConversationWithTrustedTenantAndRuntimeFields() {
        doAnswer(invocation -> {
            AiConversationDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationDO.class));
        var service = new AiConversationServiceImpl(conversationMapper, messageMapper);

        long id = service.startConversation(1L, 20L, 30L, 40L, "ROBOT_VOICE");

        assertEquals(100L, id);
        ArgumentCaptor<AiConversationDO> captor = ArgumentCaptor.forClass(AiConversationDO.class);
        verify(conversationMapper).insert(captor.capture());
        AiConversationDO row = captor.getValue();
        assertEquals(1L, row.getTenantId());
        assertEquals(20L, row.getAgentId());
        assertEquals(30L, row.getRobotId());
        assertEquals(40L, row.getMemberId());
        assertEquals("ROBOT_VOICE", row.getChannel());
        assertEquals("ACTIVE", row.getStatus());
        assertNotNull(row.getStartedAt());
        assertNotNull(row.getCreatedAt());
        assertNotNull(row.getUpdatedAt());
    }

    @Test
    void retrievalIsTenantQualifiedAndPayloadCannotOverrideTenant() {
        AiConversationDO conversation = conversation(100L, 1L);
        when(conversationMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(conversation);
        var service = new AiConversationServiceImpl(conversationMapper, messageMapper);

        assertSame(conversation, service.getConversation(1L, 100L));
        verify(conversationMapper).selectByIdAndTenantId(100L, 1L);

        assertThrows(ServiceException.class, () -> service.getConversation(2L, 100L));
        verify(conversationMapper, never()).selectByIdAndTenantId(100L, 2L);
    }

    @Test
    void appendsMessagesOnlyToConversationOwnedByTenant() {
        when(conversationMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(conversation(100L, 1L));
        var service = new AiConversationServiceImpl(conversationMapper, messageMapper);

        service.appendMessage(new AiConversationService.AiConversationMessage(
                1L, 100L, "turn-1", "USER", "你好", 55L, 12, 0, 34L, "{\"source\":\"asr\"}"));

        ArgumentCaptor<AiConversationMessageDO> captor = ArgumentCaptor.forClass(AiConversationMessageDO.class);
        verify(messageMapper).insert(captor.capture());
        AiConversationMessageDO row = captor.getValue();
        assertEquals(1L, row.getTenantId());
        assertEquals(100L, row.getConversationId());
        assertEquals("turn-1", row.getTurnId());
        assertEquals("USER", row.getRole());
        assertEquals("你好", row.getContent());
        assertEquals(55L, row.getModelId());
        assertNotNull(row.getCreatedAt());
    }

    @Test
    void messageQueryHasStableConversationCreatedAtIdOrdering() throws Exception {
        Select select = AiConversationMessageMapper.class
                .getMethod("selectByConversationIdAndTenantId", long.class, long.class)
                .getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value())
                .replace((char) 96, ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
        assertTrue(sql.contains("tenant_id = #{tenantid}"));
        assertTrue(sql.contains("conversation_id = #{conversationid}"));
        assertTrue(sql.contains("order by conversation_id, created_at, id"));

        AiConversationMessageDO first = message(1L, 100L, "turn-1", LocalDateTime.of(2026, 9, 19, 10, 0), 11L);
        AiConversationMessageDO second = message(1L, 100L, "turn-2", LocalDateTime.of(2026, 9, 19, 10, 0), 12L);
        when(conversationMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(conversation(100L, 1L));
        when(messageMapper.selectByConversationIdAndTenantId(100L, 1L)).thenReturn(List.of(first, second));
        var service = new AiConversationServiceImpl(conversationMapper, messageMapper);

        assertEquals(List.of(first, second), service.listMessages(1L, 100L));
    }

    @Test
    void startsRealtimeSessionWithConversationTenantGuard() {
        when(conversationMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(conversation(100L, 1L));
        doAnswer(invocation -> {
            AiRealtimeSessionDO row = invocation.getArgument(0);
            row.setId(200L);
            return 1;
        }).when(realtimeSessionMapper).insert(any(AiRealtimeSessionDO.class));
        var service = new AiRealtimeSessionServiceImpl(realtimeSessionMapper, conversationMapper);

        long id = service.startRealtimeSession(new AiRealtimeSessionService.RealtimeSessionStart(
                1L, 100L, 20L, 30L, 40L, "NATIVE", 50L, 60L, "provider-session-1"));

        assertEquals(200L, id);
        ArgumentCaptor<AiRealtimeSessionDO> captor = ArgumentCaptor.forClass(AiRealtimeSessionDO.class);
        verify(realtimeSessionMapper).insert(captor.capture());
        AiRealtimeSessionDO row = captor.getValue();
        assertEquals(1L, row.getTenantId());
        assertEquals(100L, row.getConversationId());
        assertEquals("NATIVE", row.getMode());
        assertEquals("provider-session-1", row.getProviderSessionId());
        assertEquals(0, row.getInterruptCount());
        assertEquals("CONNECTED", row.getStatus());
        assertNotNull(row.getConnectedAt());
    }

    @Test
    void abnormalRealtimeTerminationPersistsErrorCode() {
        AiRealtimeSessionDO existing = new AiRealtimeSessionDO();
        existing.setId(200L);
        existing.setTenantId(1L);
        existing.setStatus("CONNECTED");
        when(realtimeSessionMapper.selectByIdAndTenantId(200L, 1L)).thenReturn(existing);
        var service = new AiRealtimeSessionServiceImpl(realtimeSessionMapper, conversationMapper);
        LocalDateTime firstAudio = LocalDateTime.of(2026, 9, 19, 10, 1);
        LocalDateTime firstResponse = LocalDateTime.of(2026, 9, 19, 10, 1, 1);
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 19, 10, 2);

        service.finishRealtimeSession(1L, 200L, new AiRealtimeSessionService.RealtimeSessionFinish(
                "ERROR", "PROVIDER_TIMEOUT", firstAudio, firstResponse, 2, endedAt));

        ArgumentCaptor<AiRealtimeSessionDO> captor = ArgumentCaptor.forClass(AiRealtimeSessionDO.class);
        verify(realtimeSessionMapper).updateById(captor.capture());
        AiRealtimeSessionDO updated = captor.getValue();
        assertEquals("ERROR", updated.getStatus());
        assertEquals("PROVIDER_TIMEOUT", updated.getErrorCode());
        assertEquals(firstAudio, updated.getFirstAudioAt());
        assertEquals(firstResponse, updated.getFirstResponseAt());
        assertEquals(2, updated.getInterruptCount());
        assertEquals(endedAt, updated.getEndedAt());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void persistenceObjectsMapToExactlyThreeRuntimeTables() {
        assertEquals("ai_conversation", AiConversationDO.class.getAnnotation(TableName.class).value());
        assertEquals("ai_conversation_message", AiConversationMessageDO.class.getAnnotation(TableName.class).value());
        assertEquals("ai_realtime_session", AiRealtimeSessionDO.class.getAnnotation(TableName.class).value());
    }

    private static AiConversationDO conversation(long id, long tenantId) {
        AiConversationDO row = new AiConversationDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setAgentId(20L);
        row.setRobotId(30L);
        row.setChannel("ROBOT_VOICE");
        row.setStatus("ACTIVE");
        return row;
    }

    private static AiConversationMessageDO message(long tenantId, long conversationId, String turnId,
                                                   LocalDateTime createdAt, long id) {
        AiConversationMessageDO row = new AiConversationMessageDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setConversationId(conversationId);
        row.setTurnId(turnId);
        row.setRole("USER");
        row.setContent(turnId);
        row.setCreatedAt(createdAt);
        return row;
    }
}
