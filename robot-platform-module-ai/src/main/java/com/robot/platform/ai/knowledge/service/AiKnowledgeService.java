package com.robot.platform.ai.knowledge.service;

import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeBaseDO;
import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeDocumentDO;
import com.robot.platform.ai.knowledge.dal.mysql.AiKnowledgeBaseMapper;
import com.robot.platform.ai.knowledge.dal.mysql.AiKnowledgeDocumentMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
@RequiredArgsConstructor
public class AiKnowledgeService {
    private final AiKnowledgeBaseMapper bases;
    private final AiKnowledgeDocumentMapper documents;

    public List<AiKnowledgeBaseDO> listBases(long tenantId) {
        requireTenant(tenantId);
        return bases.selectOwnedList(tenantId);
    }

    public AiKnowledgeBaseDO getBase(long tenantId, long id) {
        requireTenant(tenantId);
        AiKnowledgeBaseDO base = bases.selectOwned(tenantId, id);
        if (base == null) throw invalidParamException("Knowledge base does not exist");
        return base;
    }

    public AiKnowledgeBaseDO createBase(long tenantId, String name, String code, String description) {
        requireTenant(tenantId);
        requireText(name, "Knowledge base name is required");
        requireText(code, "Knowledge base code is required");
        AiKnowledgeBaseDO base = new AiKnowledgeBaseDO();
        base.setTenantId(tenantId);
        base.setName(name.trim());
        base.setCode(code.trim());
        base.setDescription(description);
        try { bases.insert(base); }
        catch (DuplicateKeyException exception) { throw invalidParamException("Knowledge base code already exists"); }
        return base;
    }

    public void updateBase(long tenantId, long id, String name, String code, String description) {
        AiKnowledgeBaseDO base = getBase(tenantId, id);
        requireText(name, "Knowledge base name is required");
        requireText(code, "Knowledge base code is required");
        base.setName(name.trim());
        base.setCode(code.trim());
        base.setDescription(description);
        try { bases.updateById(base); }
        catch (DuplicateKeyException exception) { throw invalidParamException("Knowledge base code already exists"); }
    }

    public void deleteBase(long tenantId, long id) {
        getBase(tenantId, id);
        if (documents.countOwnedByBase(tenantId, id) > 0) {
            throw invalidParamException("Delete knowledge base documents first");
        }
        bases.logicalDeleteOwned(tenantId, id);
    }

    public List<AiKnowledgeDocumentDO> listDocuments(long tenantId, long baseId) {
        getBase(tenantId, baseId);
        return documents.selectOwnedList(tenantId, baseId);
    }

    public AiKnowledgeDocumentDO getDocument(long tenantId, long baseId, long id) {
        getBase(tenantId, baseId);
        AiKnowledgeDocumentDO document = documents.selectOwned(tenantId, baseId, id);
        if (document == null) throw invalidParamException("Knowledge document does not exist");
        return document;
    }

    public AiKnowledgeDocumentDO createDocument(long tenantId, long baseId, String title, String content) {
        getBase(tenantId, baseId);
        requireText(title, "Document title is required");
        requireText(content, "Document content is required");
        AiKnowledgeDocumentDO document = new AiKnowledgeDocumentDO();
        document.setTenantId(tenantId);
        document.setBaseId(baseId);
        document.setTitle(title.trim());
        document.setContent(content);
        documents.insert(document);
        return document;
    }

    public void updateDocument(long tenantId, long baseId, long id, String title, String content) {
        AiKnowledgeDocumentDO document = getDocument(tenantId, baseId, id);
        requireText(title, "Document title is required");
        requireText(content, "Document content is required");
        document.setTitle(title.trim());
        document.setContent(content);
        documents.updateById(document);
    }

    public void deleteDocument(long tenantId, long baseId, long id) {
        getDocument(tenantId, baseId, id);
        documents.logicalDeleteOwned(tenantId, baseId, id);
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw invalidParamException(message);
    }
}
