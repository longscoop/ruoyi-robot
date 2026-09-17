package com.robot.platform.device.device.controller.admin.vo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class DeviceInventoryUpdateReqVO { @NotNull private Long productId; @NotBlank @Size(max=128) private String deviceSn; @NotBlank @Size(max=128) private String name; }
