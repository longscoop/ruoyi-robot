package com.robot.platform.device.device.convert;
import com.robot.platform.device.device.controller.admin.vo.*;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.service.DeviceActivationResult;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.device.service.command.DeviceCreateCommand;
import com.robot.platform.device.device.service.command.DeviceInventoryUpdateCommand;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
@Mapper public interface DeviceConvert {
    DeviceConvert INSTANCE = Mappers.getMapper(DeviceConvert.class);
    DeviceCreateCommand convert(DeviceCreateReqVO source);
    DeviceInventoryUpdateCommand convert(DeviceInventoryUpdateReqVO source);
    DeviceActivateCommand convert(DeviceActivateReqVO source);
    DeviceRespVO convert(DeviceDO source);
    DeviceActivationResultVO convert(DeviceActivationResult source);
}
