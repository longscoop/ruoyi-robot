package com.robot.platform.ai.memory.service;

import com.robot.platform.ai.memory.MemoryScopeValidator;
import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class MySqlMemoryStore implements MemoryStore {
    private final AiMemoryMapper mapper;
    private final MemoryScopeValidator scopeValidator = new MemoryScopeValidator();

    public MySqlMemoryStore(AiMemoryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public long save(MemoryWrite write) {
        Objects.requireNonNull(write, "write");
        scopeValidator.validate(write.scope(), write.memberId(), write.robotId());
        LocalDateTime now = LocalDateTime.now();
        AiMemoryDO row = new AiMemoryDO();
        row.setTenantId(write.tenantId());
        row.setScope(write.scope());
        row.setMemberId(write.memberId());
        row.setRobotId(write.robotId());
        row.setMemoryType(write.memoryType());
        row.setContent(write.content());
        row.setSummary(write.summary());
        row.setImportance(write.importance());
        row.setConfidence(write.confidence());
        row.setSourceConversationId(write.sourceConversationId());
        row.setSourceMessageId(write.sourceMessageId());
        row.setFirstObservedAt(write.firstObservedAt() == null ? now : write.firstObservedAt());
        row.setLastObservedAt(write.lastObservedAt() == null ? now : write.lastObservedAt());
        row.setExpiresAt(write.expiresAt());
        row.setStatus("ACTIVE");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        if (row.getId() == null) throw new IllegalStateException("Failed to persist memory");
        return row.getId();
    }

    @Override
    public void supersede(long tenantId, long memoryId, Long replacementId) {
        updateStatus(tenantId, memoryId, "SUPERSEDED");
    }

    @Override
    public void delete(long tenantId, long memoryId) {
        updateStatus(tenantId, memoryId, "DELETED");
    }

    private void updateStatus(long tenantId, long memoryId, String status) {
        AiMemoryDO row = mapper.selectByIdAndTenantId(memoryId, tenantId);
        if (row == null) throw new IllegalArgumentException("Memory does not exist in tenant");
        row.setStatus(status);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
    }
}
