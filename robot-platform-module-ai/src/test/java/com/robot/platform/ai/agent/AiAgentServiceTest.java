package com.robot.platform.ai.agent;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.agent.service.AiAgentServiceImpl;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAgentServiceTest {

    @Mock private AiAgentMapper agentMapper;
    @Mock private AiPromptMapper promptMapper;
    @Mock private AiModelMapper modelMapper;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void nativeRequiresRealtimeS2sModel() {
        when(promptMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(prompt(100L, 1L, 1, "system-v1"));
        when(modelMapper.selectByIdAndTenantId(200L, 1L)).thenReturn(model(200L, 1L, "CHAT"));
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        assertThrows(ServiceException.class, () -> service.create(nativeCommand(100L, 200L)));

        verify(agentMapper, never()).insert(any(AiAgentDO.class));
    }

    @Test
    void cascadeRequiresChatAsrAndTtsModels() {
        when(promptMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(prompt(100L, 1L, 1, "system-v1"));
        when(modelMapper.selectByIdAndTenantId(201L, 1L)).thenReturn(model(201L, 1L, "CHAT"));
        when(modelMapper.selectByIdAndTenantId(202L, 1L)).thenReturn(model(202L, 1L, "ASR"));
        when(modelMapper.selectByIdAndTenantId(203L, 1L)).thenReturn(model(203L, 1L, "TTS"));
        doAnswer(invocation -> {
            AiAgentDO row = invocation.getArgument(0);
            row.setId(300L);
            return 1;
        }).when(agentMapper).insert(any(AiAgentDO.class));
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        AiAgentDO result = service.create(cascadeCommand(100L, 201L, 202L, 203L));

        assertEquals(300L, result.getId());
        assertEquals("CASCADE", result.getRealtimeMode());
        assertEquals(201L, result.getConversationModelId());
        assertEquals(202L, result.getAsrModelId());
        assertEquals(203L, result.getTtsModelId());
    }

    @Test
    void autoRequiresBothNativeAndCascadePaths() {
        when(promptMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(prompt(100L, 1L, 1, "system-v1"));
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        assertThrows(ServiceException.class, () -> service.create(autoCommand(
                100L, 201L, null, 202L, 203L)));

        verify(agentMapper, never()).insert(any(AiAgentDO.class));
    }

    @Test
    void referencedModelMustBelongToSameTenant() {
        when(promptMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(prompt(100L, 1L, 1, "system-v1"));
        when(modelMapper.selectByIdAndTenantId(204L, 1L)).thenReturn(null);
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        assertThrows(ServiceException.class, () -> service.create(nativeCommand(100L, 204L)));

        verify(agentMapper, never()).insert(any(AiAgentDO.class));
    }

    @Test
    void resolvedConfigKeepsAgentsExplicitPromptVersionUntilUpdated() {
        AiAgentDO agent = agent(300L, 1L, 100L);
        when(agentMapper.selectByIdAndTenantId(300L, 1L)).thenReturn(agent);
        when(promptMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(prompt(100L, 1L, 1, "system-v1"));
        when(modelMapper.selectByIdAndTenantId(204L, 1L)).thenReturn(model(204L, 1L, "REALTIME_S2S"));
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        AiAgentConfig config = service.getResolvedConfig(1L, 300L);

        assertEquals(100L, config.promptId());
        assertEquals(1, config.promptVersion());
        assertEquals("system-v1", config.systemPrompt());
        verify(promptMapper, never()).selectLatestByCodeAndTenantId(anyString(), anyLong());
    }

    @Test
    void updateCanExplicitlySwitchPromptVersion() {
        AiAgentDO existing = agent(300L, 1L, 100L);
        when(agentMapper.selectByIdAndTenantId(300L, 1L)).thenReturn(existing);
        when(promptMapper.selectByIdAndTenantId(101L, 1L)).thenReturn(prompt(101L, 1L, 2, "system-v2"));
        when(modelMapper.selectByIdAndTenantId(204L, 1L)).thenReturn(model(204L, 1L, "REALTIME_S2S"));
        var service = new AiAgentServiceImpl(agentMapper, promptMapper, modelMapper);

        AiAgentDO updated = service.update(new AiAgentService.UpdateAgentCommand(
                1L, 300L, "小优", "xiaoyou", "家庭机器人助手", 101L,
                null, 204L, null, null, "NATIVE",
                "SESSION", false, false, false, null, "ENABLED"));

        assertEquals(101L, updated.getSystemPromptId());
        verify(agentMapper).updateById(existing);
    }

    private static AiAgentService.CreateAgentCommand nativeCommand(long promptId, Long realtimeModelId) {
        return new AiAgentService.CreateAgentCommand(
                1L, "小优", "xiaoyou", "家庭机器人助手", promptId,
                null, realtimeModelId, null, null, "NATIVE",
                "SESSION", false, false, false, null, "ENABLED");
    }

    private static AiAgentService.CreateAgentCommand cascadeCommand(long promptId, Long chat, Long asr, Long tts) {
        return new AiAgentService.CreateAgentCommand(
                1L, "小优", "xiaoyou", "家庭机器人助手", promptId,
                chat, null, asr, tts, "CASCADE",
                "LONG_TERM", true, true, false, null, "ENABLED");
    }

    private static AiAgentService.CreateAgentCommand autoCommand(
            long promptId, Long chat, Long realtime, Long asr, Long tts) {
        return new AiAgentService.CreateAgentCommand(
                1L, "小优", "xiaoyou", "家庭机器人助手", promptId,
                chat, realtime, asr, tts, "AUTO",
                "LONG_TERM", true, true, false, null, "ENABLED");
    }

    private static AiAgentDO agent(long id, long tenantId, long promptId) {
        AiAgentDO row = new AiAgentDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName("小优");
        row.setCode("xiaoyou");
        row.setSystemPromptId(promptId);
        row.setRealtimeModelId(204L);
        row.setRealtimeMode("NATIVE");
        row.setMemoryMode("SESSION");
        row.setMemoryReadEnabled(false);
        row.setMemoryWriteEnabled(false);
        row.setStatus("ENABLED");
        return row;
    }

    private static AiPromptDO prompt(long id, long tenantId, int version, String content) {
        AiPromptDO row = new AiPromptDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName("Prompt");
        row.setCode("xiaoyou-system");
        row.setType("SYSTEM");
        row.setContent(content);
        row.setVersion(version);
        row.setStatus("ENABLED");
        return row;
    }

    private static AiModelDO model(long id, long tenantId, String type) {
        AiModelDO row = new AiModelDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setProviderId(10L);
        row.setName(type);
        row.setModelCode(type.toLowerCase());
        row.setModelType(type);
        row.setStatus("ENABLED");
        return row;
    }
}
