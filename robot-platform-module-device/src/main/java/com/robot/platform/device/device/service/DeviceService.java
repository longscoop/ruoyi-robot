package com.robot.platform.device.device.service;

import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.device.service.command.DeviceCreateCommand;
import com.robot.platform.device.device.service.command.DeviceInventoryUpdateCommand;

import java.util.List;

public interface DeviceService {
    long createInventoryDevice(DeviceCreateCommand command);
    void updateInventoryDevice(long deviceId, DeviceInventoryUpdateCommand command);
    void deleteInventoryDevice(long deviceId);
    List<DeviceDO> listInventory();
    DeviceActivationResult activate(long deviceId, DeviceActivateCommand command);
    void unbind(long deviceId);
    void changeLifecycle(long deviceId, DeviceLifecycle lifecycle);
    DeviceActivationResult rotateCredentials(long deviceId);
    DeviceDO get(long deviceId);
    List<DeviceDO> listMine();
}
