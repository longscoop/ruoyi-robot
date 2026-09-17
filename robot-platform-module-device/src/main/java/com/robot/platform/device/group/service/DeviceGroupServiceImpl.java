package com.robot.platform.device.group.service;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.group.dal.dataobject.*;
import com.robot.platform.device.group.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.device.device.enums.DeviceErrorCodeConstants.*;
@Service @RequiredArgsConstructor public class DeviceGroupServiceImpl implements DeviceGroupService {
    private final DeviceGroupMapper groupMapper; private final DeviceGroupRelationMapper relationMapper; private final DeviceMapper deviceMapper;
    @Override @Transactional(rollbackFor = Exception.class) public long create(String name, String remark) { DeviceGroupDO g=DeviceGroupDO.builder().tenantId(tenant()).name(name).remark(remark).build(); groupMapper.insert(g); return g.getId(); }
    @Override @Transactional(rollbackFor = Exception.class) public void update(long id,String name,String remark) { DeviceGroupDO g=requireGroup(id); g.setName(name);g.setRemark(remark);groupMapper.updateById(g); }
    @Override @Transactional(rollbackFor = Exception.class) public void delete(long id) { requireGroup(id); relationMapper.physicalDeleteByGroup(id); groupMapper.deleteById(id); }
    @Override @Transactional(rollbackFor = Exception.class) public void addDevice(long groupId,long deviceId) { long tenant=tenant(); requireGroup(groupId); DeviceDO d=deviceMapper.selectById(deviceId); if(d==null || !Long.valueOf(tenant).equals(d.getTenantId())) throw exception(DEVICE_GROUP_SCOPE_FORBIDDEN); try { relationMapper.insert(DeviceGroupRelationDO.builder().tenantId(tenant).groupId(groupId).deviceId(deviceId).build()); } catch(DuplicateKeyException ignored) { /* membership is idempotent */ } }
    @Override @Transactional(rollbackFor = Exception.class) public void removeDevice(long groupId,long deviceId) { requireGroup(groupId); relationMapper.physicalDeleteByGroupAndDevice(groupId,deviceId); }
    @Override public List<DeviceGroupDO> listMine() { return groupMapper.selectList(DeviceGroupDO::getTenantId, tenant()); }
    private long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private DeviceGroupDO requireGroup(long id) { DeviceGroupDO g=groupMapper.selectByIdAndTenantId(id,tenant()); if(g==null) throw exception(DEVICE_GROUP_NOT_EXISTS); return g; }
}
