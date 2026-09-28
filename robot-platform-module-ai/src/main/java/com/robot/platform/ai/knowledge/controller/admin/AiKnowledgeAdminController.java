package com.robot.platform.ai.knowledge.controller.admin;

import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeBaseDO;
import com.robot.platform.ai.knowledge.dal.dataobject.AiKnowledgeDocumentDO;
import com.robot.platform.ai.knowledge.service.AiKnowledgeService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/ai/knowledge-bases")
@RequiredArgsConstructor
public class AiKnowledgeAdminController {
    private final AiKnowledgeService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<List<BaseResp>> listBases() {
        return success(service.listBases(tenantId()).stream().map(AiKnowledgeAdminController::toResp).toList());
    }

    @GetMapping("/{baseId}")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<BaseResp> getBase(@PathVariable long baseId) {
        return success(toResp(service.getBase(tenantId(), baseId)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('ai:knowledge:create')")
    public CommonResult<Long> createBase(@Valid @RequestBody BaseReq request) {
        return success(service.createBase(tenantId(), request.name(), request.code(), request.description()).getId());
    }

    @PutMapping("/{baseId}")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:update')")
    public CommonResult<Boolean> updateBase(@PathVariable long baseId, @Valid @RequestBody BaseReq request) {
        service.updateBase(tenantId(), baseId, request.name(), request.code(), request.description());
        return success(true);
    }

    @DeleteMapping("/{baseId}")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:delete')")
    public CommonResult<Boolean> deleteBase(@PathVariable long baseId) {
        service.deleteBase(tenantId(), baseId);
        return success(true);
    }

    @GetMapping("/{baseId}/documents")
    @PreAuthorize("@ss.hasPermission('ai:document:query')")
    public CommonResult<List<DocumentResp>> listDocuments(@PathVariable long baseId) {
        return success(service.listDocuments(tenantId(), baseId).stream().map(AiKnowledgeAdminController::toResp).toList());
    }

    @GetMapping("/{baseId}/documents/{id}")
    @PreAuthorize("@ss.hasPermission('ai:document:query')")
    public CommonResult<DocumentResp> getDocument(@PathVariable long baseId, @PathVariable long id) {
        return success(toResp(service.getDocument(tenantId(), baseId, id)));
    }

    @PostMapping("/{baseId}/documents")
    @PreAuthorize("@ss.hasPermission('ai:document:create')")
    public CommonResult<Long> createDocument(@PathVariable long baseId, @Valid @RequestBody DocumentReq request) {
        return success(service.createDocument(tenantId(), baseId, request.title(), request.content()).getId());
    }

    @PutMapping("/{baseId}/documents/{id}")
    @PreAuthorize("@ss.hasPermission('ai:document:update')")
    public CommonResult<Boolean> updateDocument(@PathVariable long baseId, @PathVariable long id,
                                                @Valid @RequestBody DocumentReq request) {
        service.updateDocument(tenantId(), baseId, id, request.title(), request.content());
        return success(true);
    }

    @DeleteMapping("/{baseId}/documents/{id}")
    @PreAuthorize("@ss.hasPermission('ai:document:delete')")
    public CommonResult<Boolean> deleteDocument(@PathVariable long baseId, @PathVariable long id) {
        service.deleteDocument(tenantId(), baseId, id);
        return success(true);
    }

    private static long tenantId() { return TenantContextHolder.getRequiredTenantId(); }

    private static BaseResp toResp(AiKnowledgeBaseDO row) {
        return new BaseResp(row.getId(), row.getName(), row.getCode(), row.getDescription(), row.getCreateTime());
    }

    private static DocumentResp toResp(AiKnowledgeDocumentDO row) {
        return new DocumentResp(row.getId(), row.getBaseId(), row.getTitle(), row.getContent(), row.getUpdateTime());
    }

    public record BaseReq(@NotBlank String name, @NotBlank String code, String description) {}
    public record DocumentReq(@NotBlank String title, @NotBlank String content) {}
    public record BaseResp(Long id, String name, String code, String description, java.time.LocalDateTime createTime) {}
    public record DocumentResp(Long id, Long baseId, String title, String content, java.time.LocalDateTime updateTime) {}
}
