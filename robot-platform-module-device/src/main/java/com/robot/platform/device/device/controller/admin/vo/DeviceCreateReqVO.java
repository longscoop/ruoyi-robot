package com.robot.platform.device.device.controller.admin.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data @Schema(description = "设备入库 Request VO") public class DeviceCreateReqVO {
    @NotNull private Long productId;
    @NotBlank @Size(max = 128) private String deviceSn;
    @NotBlank @Size(max = 128) private String name;
}
