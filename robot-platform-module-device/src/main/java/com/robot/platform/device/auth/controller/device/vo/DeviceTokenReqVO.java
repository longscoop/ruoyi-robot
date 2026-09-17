package com.robot.platform.device.auth.controller.device.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Do not add a toString: this request contains an authentication proof. */
@Data
public class DeviceTokenReqVO {
    @NotBlank private String deviceSn;
    @NotNull private Long timestamp;
    @NotBlank private String nonce;
    @NotBlank private String signature;
}
