package com.robot.platform.device.device.controller.admin.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
/** Returned only from activation/rotation. Never use as a detail response. */
@Data @ToString(exclude = {"mqttSecret", "httpSecret"}) @Schema(description = "设备一次性凭据 Response VO") public class DeviceActivationResultVO {
    private Long deviceId; private Long robotId; private String mqttUsername; private String mqttSecret; private String httpSecret; private Integer credentialVersion;
}
