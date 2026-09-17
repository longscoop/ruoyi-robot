package com.robot.platform.device.device.controller.admin.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data @Schema(description = "设备激活 Request VO") public class DeviceActivateReqVO {
    @NotBlank @Size(max = 64) private String robotCode;
    @NotBlank @Size(max = 128) private String robotName;
}
