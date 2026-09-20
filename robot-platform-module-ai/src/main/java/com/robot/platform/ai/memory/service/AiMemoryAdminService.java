package com.robot.platform.ai.memory.service;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AiMemoryAdminService {
    private final AiMemoryMapper mapper;
    public AiMemoryAdminService(AiMemoryMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public List<AiMemoryDO> list(long tenantId) { return mapper.selectAllByTenantId(tenantId); }

    public AiMemoryDO get(long tenantId, long id) {
        AiMemoryDO row = mapper.selectByIdAndTenantId(id, tenantId);
        if (row == null) throw new IllegalArgumentException("Memory not found");
        return row;
    }

    public void update(long tenantId, long id, String content, String summary,
                       BigDecimal importance, LocalDateTime expiresAt) {
        AiMemoryDO row = get(tenantId, id);
        row.setContent(content);
        row.setSummary(summary);
        row.setImportance(importance);
        row.setExpiresAt(expiresAt);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
    }

    public void delete(long tenantId, long id) {
        AiMemoryDO row = get(tenantId, id);
        row.setStatus("DELETED");
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
    }
}
