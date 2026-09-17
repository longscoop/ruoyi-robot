package com.robot.platform.ai.model;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.model.service.AiModelServiceImpl;
import com.robot.platform.ai.model.service.CreateModelCommand;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiModelServiceTest {
    private final AiModelMapper mapper = mock(AiModelMapper.class);
    private final AiModelProviderMapper providerMapper = mock(AiModelProviderMapper.class);
    private final AiModelService service = new AiModelServiceImpl(mapper, providerMapper);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsProviderOutsideTenant() {
        when(providerMapper.selectByIdAndTenantId(8L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(new CreateModelCommand(1L, 8L, "DeepSeek Chat", "deepseek-chat",
                "CHAT", null, null, "ENABLED"))).isInstanceOf(ServiceException.class);

        verify(mapper, never()).insert(any());
    }

    @Test
    void validatesRequiredModelType() {
        AiModelDO model = AiModelDO.builder().id(5L).tenantId(1L).modelType("CHAT").build();
        when(mapper.selectByIdAndTenantId(5L, 1L)).thenReturn(model);

        assertThatThrownBy(() -> service.requireType(1L, 5L, "REALTIME_S2S"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void createsOnlyWithTenantOwnedProvider() {
        when(providerMapper.selectByIdAndTenantId(8L, 1L))
                .thenReturn(AiModelProviderDO.builder().id(8L).tenantId(1L).build());

        service.create(new CreateModelCommand(1L, 8L, "DeepSeek Chat", "deepseek-chat",
                "CHAT", null, null, "ENABLED"));

        verify(mapper).insert(argThat(row -> row.getTenantId().equals(1L)
                && row.getProviderId().equals(8L)
                && "CHAT".equals(row.getModelType())));
    }
}
