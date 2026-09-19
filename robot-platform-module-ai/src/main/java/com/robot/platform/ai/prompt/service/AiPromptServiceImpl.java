package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiPromptServiceImpl implements AiPromptService {
    private static final Set<String> SUPPORTED_TYPES =
            Set.of("SYSTEM", "MEMORY_EXTRACT", "MEMORY_SUMMARY", "TOOL_ROUTING");
    private static final String DEFAULT_STATUS = "ENABLED";

    private final AiPromptMapper mapper;

    public AiPromptServiceImpl(AiPromptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiPromptDO create(CreatePromptCommand command) {
        requireTenant(command.tenantId());
        requireText(command.name(), "Prompt name must not be blank");
        requireText(command.code(), "Prompt code must not be blank");
        requireText(command.content(), "Prompt content must not be blank");
        String type = requireType(command.type());

        AiPromptDO latest = mapper.selectLatestByCodeAndTenantId(command.code().trim(), command.tenantId());
        int version = latest == null ? 1 : latest.getVersion() + 1;

        AiPromptDO row = new AiPromptDO();
        row.setTenantId(command.tenantId());
        row.setName(command.name().trim());
        row.setCode(command.code().trim());
        row.setType(type);
        row.setContent(command.content());
        row.setVersion(version);
        row.setStatus(normalizeStatus(command.status()));
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw invalidParamException("Prompt version already exists; retry creation");
        }
        return row;
    }

    @Override
    public AiPromptDO get(long tenantId, long id) {
        requireTenant(tenantId);
        AiPromptDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) {
            throw invalidParamException("AI prompt does not exist");
        }
        return row;
    }

    @Override
    public List<AiPromptDO> list(long tenantId) {
        requireTenant(tenantId);
        return mapper.selectByTenantId(tenantId);
    }

    private static String requireType(String type) {
        if (type == null || type.isBlank()) {
            throw invalidParamException("Prompt type must not be blank");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw invalidParamException("Unsupported prompt type: {}", normalized);
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? DEFAULT_STATUS : status.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalidParamException(message);
        }
    }

    private static void requireTenant(long tenantId) {
        if (TenantContextHolder.getRequiredTenantId() != tenantId) {
            throw invalidParamException("AI tenant context mismatch");
        }
    }
}
