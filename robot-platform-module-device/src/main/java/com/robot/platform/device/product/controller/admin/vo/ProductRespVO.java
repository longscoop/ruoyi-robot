package com.robot.platform.device.product.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 产品型号 Response VO")
@Data
public class ProductRespVO {
    private Long id;
    private Long tenantId;
    private String productKey;
    private String name;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
