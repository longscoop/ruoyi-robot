package com.robot.platform.ai.prompt;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.ai.prompt.service.AiPromptServiceImpl;
import com.robot.platform.ai.prompt.service.CreatePromptCommand;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiPromptServiceTest {
    private final AiPromptMapper mapper = mock(AiPromptMapper.class);
    private final AiPromptService service = new AiPromptServiceImpl(mapper);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void createsNextImmutableVersion() {
        when(mapper.selectLatestByCodeAndTenantId("xiaoyou-system", 1L))
                .thenReturn(AiPromptDO.builder().id(2L).tenantId(1L).code("xiaoyou-system").version(2).build());

        AiPromptDO created = service.create(new CreatePromptCommand(1L, "小哟 System", "xiaoyou-system",
                "SYSTEM", "你叫小哟", "ENABLED"));

        assertThat(created.getVersion()).isEqualTo(3);
        assertThat(created.getContent()).isEqualTo("你叫小哟");
        verify(mapper).insert(created);
        verify(mapper, never()).updateById(any());
    }
}
