package com.robot.platform.ai.memory;

import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.memory.service.AiMemoryAdminService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiMemoryAdminServiceTest {
    @Test void categoryIsValidatedAndUpdateRemainsTenantScoped() {
        var mapper = mock(AiMemoryMapper.class);
        var service = new AiMemoryAdminService(mapper);
        when(mapper.updateActiveByTenantId(5,1,"用户工作",null,BigDecimal.ONE,null,"WORK")).thenReturn(1);
        service.update(1,5,"用户工作",null,BigDecimal.ONE,null,"work");
        verify(mapper).updateActiveByTenantId(5,1,"用户工作",null,BigDecimal.ONE,null,"WORK");
        assertThrows(IllegalArgumentException.class, () -> service.update(2,5,"用户工作",null,BigDecimal.ONE,null,"WORK"));
        assertThrows(IllegalArgumentException.class, () -> service.update(1,5,"内容",null,BigDecimal.ONE,null,"BAD"));
        verify(mapper, never()).updateActiveByTenantId(5,1,"内容",null,BigDecimal.ONE,null,"BAD");
    }
    @Test void olderClientsCanOmitCategory() {
        var mapper = mock(AiMemoryMapper.class);
        when(mapper.updateActiveByTenantId(5,1,"内容",null,BigDecimal.ONE,null,null)).thenReturn(1);
        new AiMemoryAdminService(mapper).update(1,5,"内容",null,BigDecimal.ONE,null);
        verify(mapper).updateActiveByTenantId(5,1,"内容",null,BigDecimal.ONE,null,null);
    }
}
