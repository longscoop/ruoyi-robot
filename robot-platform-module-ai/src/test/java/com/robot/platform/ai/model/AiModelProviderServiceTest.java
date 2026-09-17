package com.robot.platform.ai.model;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.ai.model.security.AiSecretCipher;
import com.robot.platform.ai.model.service.AiModelProviderService;
import com.robot.platform.ai.model.service.AiModelProviderServiceImpl;
import com.robot.platform.ai.model.service.CreateProviderCommand;
import com.robot.platform.ai.model.service.UpdateProviderCommand;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiModelProviderServiceTest {
    private final AiModelProviderMapper mapper = mock(AiModelProviderMapper.class);
    private final AiSecretCipher cipher = mock(AiSecretCipher.class);
    private final AiModelProviderService service = new AiModelProviderServiceImpl(mapper, cipher);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void encryptsPlaintextBeforePersistence() {
        when(cipher.encrypt("plain-key")).thenReturn("ciphertext");
        doAnswer(invocation -> {
            AiModelProviderDO row = invocation.getArgument(0);
            row.setId(11L);
            return 1;
        }).when(mapper).insert(any());

        long id = service.create(new CreateProviderCommand(1L, "Qwen", "qwen", "QWEN",
                "https://dashscope.aliyuncs.com", "plain-key", null, "ENABLED"));

        assertThat(id).isEqualTo(11L);
        ArgumentCaptor<AiModelProviderDO> captor = ArgumentCaptor.forClass(AiModelProviderDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getApiKeyCiphertext()).isEqualTo("ciphertext");
        assertThat(captor.getValue().getApiKeyCiphertext()).isNotEqualTo("plain-key");
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
    }

    @Test
    void blankKeyOnUpdatePreservesExistingCiphertext() {
        AiModelProviderDO existing = AiModelProviderDO.builder().id(7L).tenantId(1L).apiKeyCiphertext("old-cipher").build();
        when(mapper.selectByIdAndTenantId(7L, 1L)).thenReturn(existing);

        service.update(new UpdateProviderCommand(1L, 7L, "Qwen", "qwen", "QWEN",
                "https://dashscope.aliyuncs.com", "", null, "ENABLED"));

        assertThat(existing.getApiKeyCiphertext()).isEqualTo("old-cipher");
        verify(cipher, never()).encrypt(anyString());
        verify(mapper).updateById(existing);
    }

    @Test
    void rejectsTenantMismatchBeforeMutation() {
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> service.create(new CreateProviderCommand(1L, "Qwen", "qwen", "QWEN",
                "https://dashscope.aliyuncs.com", "plain-key", null, "ENABLED")))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(mapper, cipher);
    }
}
