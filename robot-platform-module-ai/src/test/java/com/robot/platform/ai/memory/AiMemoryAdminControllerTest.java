package com.robot.platform.ai.memory;

import com.robot.platform.ai.memory.controller.admin.AiMemoryAdminController;
import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;
import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.memory.service.AiMemoryAdminService;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiMemoryAdminControllerTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void crossTenantIdNeverFallsBackToUnscopedLookup() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        AiMemoryAdminService service = new AiMemoryAdminService(mapper);
        when(mapper.selectByIdAndTenantId(9L, 42L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.get(42L, 9L));
        verify(mapper).selectByIdAndTenantId(9L, 42L);
        verify(mapper, never()).selectById(any());
    }

    @Test void updateOnlyChangesEditableFieldsOnTenantOwnedRow() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        AiMemoryDO row = new AiMemoryDO();
        row.setId(9L); row.setTenantId(42L); row.setScope("MEMBER"); row.setMemberId(7L); row.setStatus("ACTIVE");
        when(mapper.selectByIdAndTenantId(9L, 42L)).thenReturn(row);
        new AiMemoryAdminService(mapper).update(42L, 9L, "new", "summary", new BigDecimal("0.80"), null);
        assertEquals("MEMBER", row.getScope());
        assertEquals(7L, row.getMemberId());
        assertEquals("new", row.getContent());
        verify(mapper).updateById(row);
    }

    @Test void deleteIsLogicalAndTenantScoped() {
        AiMemoryMapper mapper = mock(AiMemoryMapper.class);
        AiMemoryDO row = new AiMemoryDO(); row.setId(9L); row.setTenantId(42L); row.setStatus("ACTIVE");
        when(mapper.selectByIdAndTenantId(9L, 42L)).thenReturn(row);
        new AiMemoryAdminService(mapper).delete(42L, 9L);
        assertEquals("DELETED", row.getStatus());
        verify(mapper).updateById(row);
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test void controllerUsesCurrentTenantAndDoesNotAcceptTenantId() {
        TenantContextHolder.setTenantId(42L);
        AiMemoryAdminService service = mock(AiMemoryAdminService.class);
        AiMemoryAdminController controller = new AiMemoryAdminController(service);
        controller.list();
        verify(service).list(42L);
        assertTrue(java.util.Arrays.stream(AiMemoryAdminController.MemoryUpdateReqVO.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equals("tenantId")));
    }
}
