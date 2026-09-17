package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.robot.platform.ai.enums.AiErrorCodeConstants.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl implements AiPromptService {
    private static final Set<String> PROMPT_TYPES = Set.of("SYSTEM", "MEMORY_EXTRACT", "MEMORY_SUMMARY", "TOOL_ROUTING");

    private final AiPromptMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptDO create(CreatePromptCommand command) {
        requireCurrentTenant(command.tenantId());
        if (!PROMPT_TYPES.contains(command.type())) {
            throw exception(AI_AGENT_CONFIG_INVALID);
        }
        AiPromptDO latest = mapper.selectLatestByCodeAndTenantId(command.code(), command.tenantId());
        int nextVersion = latest == null ? 1 : latest.getVersion() + 1;
        AiPromptDO row = AiPromptDO.builder()
                .tenantId(command.tenantId())
                .name(command.name())
                .code(command.code())
                .type(command.type())
                .content(command.content())
                .version(nextVersion)
                .status(command.status() == null || command.status().isBlank() ? "ENABLED" : command.status())
                .build();
        mapper.insert(row);
        return row;
    }

    @Override
    public AiPromptDO get(long tenantId, long id) {
        requireCurrentTenant(tenantId);
        AiPromptDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw exception(AI_PROMPT_NOT_EXISTS);
        }
        return row;
    }

    @Override
    public List<AiPromptDO> listVersions(long tenantId, String code) {
        requireCurrentTenant(tenantId);
        return mapper.selectByCodeAndTenantId(code, tenantId);
    }

    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) {
            throw exception(AI_TENANT_FORBIDDEN);
        }
    }
}
