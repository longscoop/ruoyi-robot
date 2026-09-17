package com.robot.platform.device.device.service.command;
import lombok.Data;
/** Tenant and product scope are exclusively server-derived. */
@Data public class DeviceActivateCommand { private String robotCode; private String robotName; }
