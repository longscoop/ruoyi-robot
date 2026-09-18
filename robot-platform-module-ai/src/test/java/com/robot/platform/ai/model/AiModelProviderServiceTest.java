package com.robot.platform.ai.model;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.security.AiSecretCipher;
import com.robot.platform.ai.model.service.AiModelProviderService;
import com.robot.platform.ai.model.service.AiModelProviderServiceImpl;
import com.robot.platform.ai.model.service.AiModelService;
import com.robot.platform.ai.model.service.AiModelServiceImpl;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiModelProviderServiceTest {

    @Mock
    private AiModelProviderMapper providerMapper;
    @Mock
    private AiModelMapper modelMapper;
    @Mock
    private AiSecretCipher cipher;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void createEncryptsSecretBeforePersistenceAndResponseDoesNotExposeIt() {
        when(cipher.encrypt("plain-key")).thenReturn("cipher-key");
        doAnswer(invocation -> {
            AiModelProviderDO row = invocation.getArgument(0);
            row.setId(10L);
            return 1;
        }).when(providerMapper).insert(any(AiModelProviderDO.class));
        var service = new AiModelProviderServiceImpl(providerMapper, cipher);

        var result = service.create(new AiModelProviderService.CreateProviderCommand(
                1L, "Qwen", "qwen", "QWEN", "https://example.invalid", "plain-key", null));

        ArgumentCaptor<AiModelProviderDO> captor = ArgumentCaptor.forClass(AiModelProviderDO.class);
        verify(providerMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getTenantId());
        assertEquals("cipher-key", captor.getValue().getApiKeyCiphertext());
        assertNotEquals("plain-key", captor.getValue().getApiKeyCiphertext());
        assertTrue(result.apiKeyConfigured());
        assertTrue(Arrays.stream(result.getClass().getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .noneMatch(name -> name.equals("apikey") || name.contains("ciphertext")));
    }

    @Test
    void tenantMismatchIsRejectedBeforeAnyMapperMutation() {
        TenantContextHolder.setTenantId(2L);
        var service = new AiModelProviderServiceImpl(providerMapper, cipher);

        assertThrows(ServiceException.class, () -> service.create(
                new AiModelProviderService.CreateProviderCommand(
                        1L, "Qwen", "qwen", "QWEN", "https://example.invalid", "plain-key", null)));

        verifyNoInteractions(providerMapper, cipher);
    }

    @Test
    void blankKeyOnUpdatePreservesExistingCiphertext() {
        AiModelProviderDO existing = provider(10L, 1L, "cipher-old");
        when(providerMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(existing);
        var service = new AiModelProviderServiceImpl(providerMapper, cipher);

        var result = service.update(new AiModelProviderService.UpdateProviderCommand(
                1L, 10L, "Qwen New", "qwen", "QWEN", "https://example.invalid/v2", "", "{}", "ENABLED"));

        ArgumentCaptor<AiModelProviderDO> captor = ArgumentCaptor.forClass(AiModelProviderDO.class);
        verify(providerMapper).updateById(captor.capture());
        verifyNoInteractions(cipher);
        assertEquals("cipher-old", captor.getValue().getApiKeyCiphertext());
        assertTrue(result.apiKeyConfigured());
    }

    @Nested
    class ModelServiceValidation {

        @Test
        void createRequiresProviderOwnedBySameTenant() {
            when(providerMapper.selectByIdAndTenantId(20L, 1L)).thenReturn(provider(20L, 1L, null));
            doAnswer(invocation -> {
                AiModelDO row = invocation.getArgument(0);
                row.setId(30L);
                return 1;
            }).when(modelMapper).insert(any(AiModelDO.class));
            var service = new AiModelServiceImpl(modelMapper, providerMapper);

            AiModelDO result = service.create(new AiModelService.CreateModelCommand(
                    1L, 20L, "Qwen Realtime", "qwen-realtime", "REALTIME_S2S", "{}", null, "ENABLED"));

            assertEquals(30L, result.getId());
            assertEquals(1L, result.getTenantId());
            assertEquals(20L, result.getProviderId());
            assertEquals("REALTIME_S2S", result.getModelType());
        }

        @Test
        void createRejectsUnsupportedModelType() {
            var service = new AiModelServiceImpl(modelMapper, providerMapper);

            assertThrows(ServiceException.class, () -> service.create(new AiModelService.CreateModelCommand(
                    1L, 20L, "Bad", "bad", "IMAGE", null, null, "ENABLED")));

            verifyNoInteractions(modelMapper, providerMapper);
        }

        @Test
        void createRejectsProviderFromAnotherTenant() {
            when(providerMapper.selectByIdAndTenantId(20L, 1L)).thenReturn(null);
            var service = new AiModelServiceImpl(modelMapper, providerMapper);

            assertThrows(ServiceException.class, () -> service.create(new AiModelService.CreateModelCommand(
                    1L, 20L, "Qwen Chat", "qwen-chat", "CHAT", null, null, "ENABLED")));

            verify(modelMapper, never()).insert(any());
        }
    }

    private static AiModelProviderDO provider(long id, long tenantId, String ciphertext) {
        AiModelProviderDO provider = new AiModelProviderDO();
        provider.setId(id);
        provider.setTenantId(tenantId);
        provider.setName("Provider");
        provider.setCode("provider");
        provider.setProviderType("QWEN");
        provider.setBaseUrl("https://example.invalid");
        provider.setApiKeyCiphertext(ciphertext);
        provider.setStatus("ENABLED");
        return provider;
    }
}
