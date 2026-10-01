package com.robot.platform.ai.memory.service;

import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMemoryAdminService {

    private final AiMemoryMapper mapper;

    public List<AiMemoryDO> list(long tenantId) {
        return mapper.selectAllByTenantId(tenantId);
    }

    public void update(long tenantId, long id, String content, String summary,
                       BigDecimal importance, LocalDateTime expiresAt) {
        update(tenantId, id, content, summary, importance, expiresAt, null);
    }

    public void update(long tenantId, long id, String content, String summary,
                       BigDecimal importance, LocalDateTime expiresAt, String memoryType) {
        String category = MemoryCategories.validate(memoryType);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        if (importance == null || importance.compareTo(BigDecimal.ZERO) < 0
                || importance.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Memory importance must be between 0 and 1");
        }
        if (mapper.updateActiveByTenantId(id, tenantId, content.trim(), summary,
                importance, expiresAt, category) != 1) {
            throw new IllegalArgumentException("Active memory does not exist in tenant");
        }
    }

    public void delete(long tenantId, long id) {
        if (mapper.invalidateActiveByTenantId(id, tenantId) != 1) {
            throw new IllegalArgumentException("Active memory does not exist in tenant");
        }
    }
}
