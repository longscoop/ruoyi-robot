package com.robot.platform.device.device;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.group.dal.dataobject.DeviceGroupDO;
import com.robot.platform.device.group.dal.mysql.DeviceGroupMapper;
import com.robot.platform.device.group.dal.mysql.DeviceGroupRelationMapper;
import com.robot.platform.device.group.service.DeviceGroupService;
import com.robot.platform.device.group.service.DeviceGroupServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeviceGroupServiceTest {
 @AfterEach void clear() { TenantContextHolder.clear(); }
 @Test void rejectsAddingAnotherTenantsDeviceToGroup() {
  DeviceGroupMapper groups=mock(DeviceGroupMapper.class); DeviceGroupRelationMapper relations=mock(DeviceGroupRelationMapper.class); DeviceMapper devices=mock(DeviceMapper.class);
  DeviceGroupService service=new DeviceGroupServiceImpl(groups,relations,devices); TenantContextHolder.setTenantId(10L);
  when(groups.selectByIdAndTenantId(1L,10L)).thenReturn(DeviceGroupDO.builder().id(1L).tenantId(10L).build());
  when(devices.selectById(2L)).thenReturn(DeviceDO.builder().id(2L).tenantId(20L).build());
  assertThatThrownBy(() -> service.addDevice(1L,2L)).isInstanceOf(ServiceException.class);
  verifyNoInteractions(relations);
 }
 @Test void removesPhysicalRelationBeforeItCanBeAddedAgainAndCleansUpOnGroupDelete() {
  DeviceGroupMapper groups=mock(DeviceGroupMapper.class); DeviceGroupRelationMapper relations=mock(DeviceGroupRelationMapper.class); DeviceMapper devices=mock(DeviceMapper.class);
  DeviceGroupService service=new DeviceGroupServiceImpl(groups,relations,devices); TenantContextHolder.setTenantId(10L);
  DeviceGroupDO group=DeviceGroupDO.builder().id(1L).tenantId(10L).build();
  when(groups.selectByIdAndTenantId(1L,10L)).thenReturn(group);
  when(devices.selectById(2L)).thenReturn(DeviceDO.builder().id(2L).tenantId(10L).build());
  service.removeDevice(1L,2L); service.addDevice(1L,2L); service.delete(1L);
  verify(relations).physicalDeleteByGroupAndDevice(1L,2L); verify(relations).insert(any(com.robot.platform.device.group.dal.dataobject.DeviceGroupRelationDO.class)); verify(relations).physicalDeleteByGroup(1L);
 }
}
