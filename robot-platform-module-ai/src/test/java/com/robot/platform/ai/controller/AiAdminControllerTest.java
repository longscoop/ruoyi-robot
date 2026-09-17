package com.robot.platform.ai.controller;

import com.robot.platform.ai.model.controller.admin.AiModelProviderAdminController;
import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.service.AiModelProviderService;
import com.robot.platform.ai.model.service.CreateProviderCommand;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiAdminControllerTest {
    private final AiModelProviderService providerService = mock(AiModelProviderService.class);
    private final AiModelProviderAdminController controller = new AiModelProviderAdminController(providerService);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void providerResponsesExposeConfiguredFlagButNoSecretField() {
        when(providerService.list(1L)).thenReturn(List.of(AiModelProviderDO.builder()
                .id(7L).tenantId(1L).name("Qwen").code("qwen").providerType("QWEN")
                .baseUrl("https://example.invalid").apiKeyCiphertext("ciphertext").status("ENABLED").build()));

        var response = controller.list().getData().get(0);

        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(AiModelProviderAdminController.ProviderRespVO.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("apiKey", "apiKeyCiphertext");
        verify(providerService).list(1L);
    }

    @Test
    void createTakesTenantOnlyFromAuthenticatedTenantContext() {
        var request = new AiModelProviderAdminController.ProviderReqVO();
        request.setName("DeepSeek");
        request.setCode("deepseek");
        request.setProviderType("DEEPSEEK");
        request.setBaseUrl("https://api.deepseek.com");
        request.setApiKey("secret");
        request.setStatus("ENABLED");

        controller.create(request);

        ArgumentCaptor<CreateProviderCommand> captor = ArgumentCaptor.forClass(CreateProviderCommand.class);
        verify(providerService).create(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().apiKey()).isEqualTo("secret");
    }
}
