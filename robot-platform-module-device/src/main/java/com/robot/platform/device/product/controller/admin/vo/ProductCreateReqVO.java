package com.robot.platform.device.product.controller.admin.vo;

import com.robot.platform.framework.common.validation.InEnum;
import com.robot.platform.device.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 产品型号创建 Request VO")
@Data
public class ProductCreateReqVO {
    @NotBlank(message = "产品型号标识不能为空")
    @Size(max = 64, message = "产品型号标识长度不能超过 64")
    private String productKey;
    @NotBlank(message = "产品型号名称不能为空")
    @Size(max = 128, message = "产品型号名称长度不能超过 128")
    private String name;
    @NotNull(message = "产品型号状态不能为空")
    @InEnum(value = ProductStatus.class, message = "产品型号状态必须是 {value}")
    private Integer status;
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
    /** Only platform super-admins may request the public scope. */
    private boolean publicProduct;
}
