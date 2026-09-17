package com.robot.platform.device.group.service;
import com.robot.platform.device.group.dal.dataobject.DeviceGroupDO;
import java.util.List;
public interface DeviceGroupService { long create(String name, String remark); void update(long id, String name, String remark); void delete(long id); void addDevice(long groupId, long deviceId); void removeDevice(long groupId, long deviceId); List<DeviceGroupDO> listMine(); }
