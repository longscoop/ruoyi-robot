package com.robot.platform.ai.agent;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.agent.service.AiAgentServiceImpl;
import com.robot.platform.ai.agent.service.CreateAgentCommand;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.robot.platform.ai.enums.AiErrorCodeConstants.AI_MODEL_TYPE_INVALID;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentServiceTest {
    private final AiAgentMapper mapper = mock(AiAgentMapper.class);
    private final AiPromptService promptService = mock(AiPromptService.class);
    private final AiModelService modelService = mock(AiModelService.class);
    private final AiAgentService service = new AiAgentServiceImpl(mapper, promptService, modelService);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
        when(promptService.get(1L, 10L)).thenReturn(AiPromptDO.builder().id(10L).tenantId(1L)
                .code("xiaoyou-system").type("SYSTEM").content("旧版本 Prompt").version(1).build());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void nativeModeRejectsNonRealtimeModel() {
        when(modelService.requireType(1L, 20L, "REALTIME_S2S")).thenThrow(exception(AI_MODEL_TYPE_INVALID));

        assertThatThrownBy(() -> service.create(new CreateAgentCommand(1L, "小哟", "xiaoyou", null, 10L,
                null, 20L, null, null, "NATIVE", "LONG_TERM", true, true, false, null, "ENABLED")))
                .isInstanceOf(ServiceException.class);

        verify(mapper, never()).insert(any());
    }

    @Test
    void cascadeRequiresTypedChatAsrAndTtsModels() {
        service.create(new CreateAgentCommand(1L, "小哟", "xiaoyou", null, 10L,
                21L, null, 22L, 23L, "CASCADE", "SESSION", true, false, false, null, "ENABLED"));

        verify(modelService).requireType(1L, 21L, "CHAT");
        verify(modelService).requireType(1L, 22L, "ASR");
        verify(modelService).requireType(1L, 23L, "TTS");
        verify(mapper).insert(any(AiAgentDO.class));
    }

    @Test
    void resolvedConfigUsesAgentsExplicitPromptVersion() {
        AiAgentDO agent = AiAgentDO.builder().id(7L).tenantId(1L).code("xiaoyou").systemPromptId(10L)
                .realtimeModelId(20L).realtimeMode("NATIVE").memoryMode("LONG_TERM")
                .memoryReadEnabled(true).memoryWriteEnabled(true).build();
        when(mapper.selectByIdAndTenantId(7L, 1L)).thenReturn(agent);

        AiAgentConfig config = service.getResolvedConfig(1L, 7L);

        assertThat(config.promptId()).isEqualTo(10L);
        assertThat(config.promptVersion()).isEqualTo(1);
        assertThat(config.systemPrompt()).isEqualTo("旧版本 Prompt");
        verify(promptService).get(1L, 10L);
        verify(modelService).requireType(1L, 20L, "REALTIME_S2S");
    }
}
