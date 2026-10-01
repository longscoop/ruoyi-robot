package com.robot.platform.ai.prompt.service;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.ai.prompt.dal.mysql.AiPromptMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

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
        requireText(command.content(), "Prompt content must not be blank");
        String type = requireType(command.type() == null ? "SYSTEM" : command.type());
        String code = command.code() == null || command.code().isBlank()
                ? "role-" + UUID.randomUUID() : command.code().trim();
        if (command.name().trim().length() > 128 || code.length() > 64)
            throw invalidParamException("角色名称或内部编码过长");

        AiPromptDO latest = mapper.selectLatestByCodeAndTenantId(code, command.tenantId());
        int version = latest == null ? 1 : latest.getVersion() + 1;

        AiPromptDO row = new AiPromptDO();
        row.setTenantId(command.tenantId());
        row.setName(command.name().trim());
        row.setCode(code);
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
    @Transactional
    public AiPromptDO update(long tenantId, long id, CreatePromptCommand command) {
        requireTenant(tenantId);
        if (command.tenantId() != tenantId) throw invalidParamException("AI tenant context mismatch");
        AiPromptDO row = get(tenantId, id);
        requireText(command.name(), "请填写角色名称");
        requireText(command.content(), "请填写角色提示词");
        if (command.name().trim().length() > 128) throw invalidParamException("角色名称不能超过 128 个字");
        String type = requireType(command.type() == null ? row.getType() : command.type());
        if (!type.equals(row.getType())) throw invalidParamException("已创建角色的用途不能更改");
        AiPromptDO latest = mapper.selectLatestByCodeAndTenantId(row.getCode(), tenantId);
        row.setName(command.name().trim());
        row.setContent(command.content());
        row.setStatus(normalizeStatus(command.status()));
        row.setVersion((latest == null ? row.getVersion() : latest.getVersion()) + 1);
        try {
            mapper.updateById(row);
        } catch (DuplicateKeyException error) {
            throw invalidParamException("角色刚刚被其他操作更新，请刷新后重试");
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
        String value = status == null || status.isBlank() ? DEFAULT_STATUS : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLED", "DISABLED").contains(value)) throw invalidParamException("角色状态无效");
        return value;
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
