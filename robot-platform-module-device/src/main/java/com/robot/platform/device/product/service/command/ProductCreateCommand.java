package com.robot.platform.device.product.service.command;

import lombok.Data;

/** Service command; tenant identity deliberately is not client-supplied. */
@Data
public class ProductCreateCommand {
    private String productKey;
    private String name;
    private Integer status;
    private String remark;
    private boolean publicProduct;
}
