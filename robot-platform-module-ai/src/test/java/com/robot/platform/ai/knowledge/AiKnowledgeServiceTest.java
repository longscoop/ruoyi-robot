package com.robot.platform.ai.knowledge;

import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeBaseDO;
import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeDocumentDO;
import com.robot.platform.ai.knowledge.dal.mysql.AiKnowledgeBaseMapper;
import com.robot.platform.ai.knowledge.dal.mysql.AiKnowledgeDocumentMapper;
import com.robot.platform.ai.knowledge.service.AiKnowledgeService;
import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeServiceTest {
    @Mock AiKnowledgeBaseMapper bases;
    @Mock AiKnowledgeDocumentMapper documents;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void rejectsCrossTenantMutationsBeforePersistence() {
        AiKnowledgeService service = new AiKnowledgeService(bases, documents);
        assertThrows(ServiceException.class, () -> service.createBase(2L, "Manuals", "manuals", ""));
        verifyNoInteractions(bases, documents);
    }

    @Test
    void documentCreationRequiresOwnedBase() {
        AiKnowledgeService service = new AiKnowledgeService(bases, documents);
        assertThrows(ServiceException.class, () -> service.createDocument(1L, 10L, "Intro", "Content"));
        verify(bases).selectOwned(1L, 10L);
        verify(documents, never()).insert(any(AiKnowledgeDocumentDO.class));
    }

    @Test
    void baseDeletionRefusesExistingDocuments() {
        AiKnowledgeBaseDO base = new AiKnowledgeBaseDO();
        base.setId(10L);
        when(bases.selectOwned(1L, 10L)).thenReturn(base);
        when(documents.countOwnedByBase(1L, 10L)).thenReturn(1L);
        AiKnowledgeService service = new AiKnowledgeService(bases, documents);
        assertThrows(ServiceException.class, () -> service.deleteBase(1L, 10L));
        verify(bases, never()).logicalDeleteOwned(any(Long.class), any(Long.class));
    }

    @Test
    void documentUpdateCannotTargetAnotherBaseOrTenant() {
        AiKnowledgeBaseDO base = new AiKnowledgeBaseDO();
        base.setId(10L);
        when(bases.selectOwned(1L, 10L)).thenReturn(base);
        AiKnowledgeDocumentDO document = new AiKnowledgeDocumentDO();
        document.setId(20L);
        document.setBaseId(10L);
        when(documents.selectOwned(1L, 10L, 20L)).thenReturn(document);
        AiKnowledgeService service = new AiKnowledgeService(bases, documents);
        service.updateDocument(1L, 10L, 20L, "New title", "New content");
        assertEquals("New title", document.getTitle());
        verify(documents).updateById(document);
        assertThrows(ServiceException.class, () -> service.updateDocument(1L, 11L, 20L, "X", "Y"));
    }
}
