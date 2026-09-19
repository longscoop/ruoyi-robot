package com.robot.platform.ai.controller;

import com.robot.platform.ai.agent.controller.admin.AiAgentAdminController;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.controller.admin.AiModelAdminController;
import com.robot.platform.ai.model.controller.admin.AiModelProviderAdminController;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.service.AiModelProviderService;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.prompt.controller.admin.AiPromptAdminController;
import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAdminControllerTest {

    private static final long TENANT_ID = 42L;

    @Mock private AiModelProviderService providerService;
    @Mock private AiModelService modelService;
    @Mock private AiPromptService promptService;
    @Mock private AiAgentService agentService;
    @Mock private AiAgentRobotBindingService bindingService;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void adminCreateAndBindOperationsUseOnlyCurrentTenantContext() {
        when(providerService.create(any())).thenReturn(new AiModelProviderService.ProviderView(
                10L, TENANT_ID, "Qwen", "qwen", "QWEN", "https://example.invalid",
                "{}", "ENABLED", true));
        when(modelService.create(any())).thenReturn(model(20L));
        when(promptService.create(any())).thenReturn(prompt(30L));
        when(agentService.create(any())).thenReturn(agent(40L));
        when(bindingService.bind(TENANT_ID, 40L, 50L, true)).thenReturn(60L);

        var providerRequest = new AiModelProviderAdminController.ProviderCreateReqVO();
        providerRequest.setName("Qwen");
        providerRequest.setCode("qwen");
        providerRequest.setProviderType("QWEN");
        providerRequest.setBaseUrl("https://example.invalid");
        providerRequest.setApiKey("plain-key");
        providerRequest.setConfigJson("{}");
        new AiModelProviderAdminController(providerService).create(providerRequest);

        var modelRequest = new AiModelAdminController.ModelCreateReqVO();
        modelRequest.setProviderId(10L);
        modelRequest.setName("Qwen Chat");
        modelRequest.setModelCode("qwen-chat");
        modelRequest.setModelType("CHAT");
        modelRequest.setStatus("ENABLED");
        new AiModelAdminController(modelService).create(modelRequest);

        var promptRequest = new AiPromptAdminController.PromptCreateReqVO();
        promptRequest.setName("System");
        promptRequest.setCode("system");
        promptRequest.setType("SYSTEM");
        promptRequest.setContent("You are helpful.");
        promptRequest.setStatus("ENABLED");
        new AiPromptAdminController(promptService).create(promptRequest);

        var agentRequest = new AiAgentAdminController.AgentCreateReqVO();
        agentRequest.setName("小优");
        agentRequest.setCode("xiaoyou");
        agentRequest.setSystemPromptId(30L);
        agentRequest.setRealtimeMode("NATIVE");
        agentRequest.setMemoryMode("SESSION");
        agentRequest.setRealtimeModelId(20L);
        agentRequest.setStatus("ENABLED");
        new AiAgentAdminController(agentService, bindingService).create(agentRequest);

        var bindRequest = new AiAgentAdminController.AgentBindReqVO();
        bindRequest.setRobotId(50L);
        bindRequest.setDefaultAgent(true);
        new AiAgentAdminController(agentService, bindingService).bind(40L, bindRequest);

        ArgumentCaptor<AiModelProviderService.CreateProviderCommand> providerCaptor =
                ArgumentCaptor.forClass(AiModelProviderService.CreateProviderCommand.class);
        verify(providerService).create(providerCaptor.capture());
        assertEquals(TENANT_ID, providerCaptor.getValue().tenantId());

        ArgumentCaptor<AiModelService.CreateModelCommand> modelCaptor =
                ArgumentCaptor.forClass(AiModelService.CreateModelCommand.class);
        verify(modelService).create(modelCaptor.capture());
        assertEquals(TENANT_ID, modelCaptor.getValue().tenantId());

        ArgumentCaptor<AiPromptService.CreatePromptCommand> promptCaptor =
                ArgumentCaptor.forClass(AiPromptService.CreatePromptCommand.class);
        verify(promptService).create(promptCaptor.capture());
        assertEquals(TENANT_ID, promptCaptor.getValue().tenantId());

        ArgumentCaptor<AiAgentService.CreateAgentCommand> agentCaptor =
                ArgumentCaptor.forClass(AiAgentService.CreateAgentCommand.class);
        verify(agentService).create(agentCaptor.capture());
        assertEquals(TENANT_ID, agentCaptor.getValue().tenantId());

        verify(bindingService).bind(TENANT_ID, 40L, 50L, true);
    }

    @Test
    void providerResponseExposesOnlyConfiguredFlagNeverSecretMaterial() {
        when(providerService.get(TENANT_ID, 10L)).thenReturn(new AiModelProviderService.ProviderView(
                10L, TENANT_ID, "Qwen", "qwen", "QWEN", "https://example.invalid",
                "{}", "ENABLED", true));

        var response = new AiModelProviderAdminController(providerService).get(10L).getData();

        assertTrue(response.apiKeyConfigured());
        assertNotNull(response.getClass().getRecordComponents());
        assertTrue(Arrays.stream(response.getClass().getRecordComponents())
                .map(component -> component.getName())
                .noneMatch(name -> name.equals("apiKey") || name.equals("apiKeyCiphertext")));
    }

    @Test
    void requestDtosDoNotAcceptTenantId() {
        assertNoTenantField(
                AiModelProviderAdminController.ProviderCreateReqVO.class,
                AiModelProviderAdminController.ProviderUpdateReqVO.class,
                AiModelAdminController.ModelCreateReqVO.class,
                AiModelAdminController.ModelUpdateReqVO.class,
                AiPromptAdminController.PromptCreateReqVO.class,
                AiAgentAdminController.AgentCreateReqVO.class,
                AiAgentAdminController.AgentUpdateReqVO.class,
                AiAgentAdminController.AgentBindReqVO.class);
    }

    @Test
    void controllersExposeRequiredAdminApiRoots() {
        assertRequestMapping(AiModelProviderAdminController.class, "/admin-api/ai/providers");
        assertRequestMapping(AiModelAdminController.class, "/admin-api/ai/models");
        assertRequestMapping(AiPromptAdminController.class, "/admin-api/ai/prompts");
        assertRequestMapping(AiAgentAdminController.class, "/admin-api/ai/agents");
    }

    private static void assertNoTenantField(Class<?>... requestTypes) {
        for (Class<?> requestType : requestTypes) {
            assertTrue(Arrays.stream(requestType.getDeclaredFields())
                    .noneMatch(field -> field.getName().equals("tenantId")),
                    () -> requestType.getSimpleName() + " must not accept tenantId");
        }
    }

    private static void assertRequestMapping(Class<?> controllerType, String expected) {
        RequestMapping mapping = controllerType.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{expected}, mapping.value());
    }

    private static AiModelDO model(long id) {
        AiModelDO row = new AiModelDO();
        row.setId(id);
        row.setTenantId(TENANT_ID);
        row.setProviderId(10L);
        row.setName("Qwen Chat");
        row.setModelCode("qwen-chat");
        row.setModelType("CHAT");
        row.setStatus("ENABLED");
        return row;
    }

    private static AiPromptDO prompt(long id) {
        AiPromptDO row = new AiPromptDO();
        row.setId(id);
        row.setTenantId(TENANT_ID);
        row.setName("System");
        row.setCode("system");
        row.setType("SYSTEM");
        row.setContent("You are helpful.");
        row.setVersion(1);
        row.setStatus("ENABLED");
        return row;
    }

    private static AiAgentDO agent(long id) {
        AiAgentDO row = new AiAgentDO();
        row.setId(id);
        row.setTenantId(TENANT_ID);
        row.setName("小优");
        row.setCode("xiaoyou");
        row.setSystemPromptId(30L);
        row.setRealtimeModelId(20L);
        row.setRealtimeMode("NATIVE");
        row.setMemoryMode("SESSION");
        row.setStatus("ENABLED");
        return row;
    }
}
