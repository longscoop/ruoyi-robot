package com.robot.platform.ai.prompt;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.ai.prompt.service.AiPromptServiceImpl;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPromptServiceTest {

    @Mock
    private AiPromptMapper mapper;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void createSameCodeCreatesNextImmutableVersion() {
        AiPromptDO first = prompt(11L, 1L, "xiaoyou-system", 1, "v1");
        when(mapper.selectLatestByCodeAndTenantId("xiaoyou-system", 1L))
                .thenReturn(null)
                .thenReturn(first);
        doAnswer(invocation -> {
            AiPromptDO row = invocation.getArgument(0);
            row.setId(row.getVersion() == 1 ? 11L : 12L);
            return 1;
        }).when(mapper).insert(any(AiPromptDO.class));
        var service = new AiPromptServiceImpl(mapper);

        AiPromptDO v1 = service.create(new AiPromptService.CreatePromptCommand(
                1L, "小优系统提示词", "xiaoyou-system", "SYSTEM", "v1", "ENABLED"));
        AiPromptDO v2 = service.create(new AiPromptService.CreatePromptCommand(
                1L, "小优系统提示词", "xiaoyou-system", "SYSTEM", "v2", "ENABLED"));

        assertEquals(1, v1.getVersion());
        assertEquals(2, v2.getVersion());
        assertEquals("v1", v1.getContent());
        assertEquals("v2", v2.getContent());
        verify(mapper, never()).updateById(any(AiPromptDO.class));
    }

    @Test
    void tenantMismatchIsRejectedBeforeMapperAccess() {
        TenantContextHolder.setTenantId(2L);
        var service = new AiPromptServiceImpl(mapper);

        assertThrows(ServiceException.class, () -> service.create(new AiPromptService.CreatePromptCommand(
                1L, "Prompt", "code", "SYSTEM", "content", "ENABLED")));

        verifyNoInteractions(mapper);
    }

    @Test
    void getRequiresSameTenant() {
        when(mapper.selectByIdAndTenantId(11L, 1L)).thenReturn(prompt(11L, 1L, "code", 1, "content"));
        var service = new AiPromptServiceImpl(mapper);

        assertEquals(11L, service.get(1L, 11L).getId());
        verify(mapper).selectByIdAndTenantId(11L, 1L);
    }

    private static AiPromptDO prompt(long id, long tenantId, String code, int version, String content) {
        AiPromptDO row = new AiPromptDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName("Prompt");
        row.setCode(code);
        row.setType("SYSTEM");
        row.setContent(content);
        row.setVersion(version);
        row.setStatus("ENABLED");
        return row;
    }
}
