package com.robot.platform.device.product.service.command;

import lombok.Data;

/** Service command; scope cannot be moved by an update. */
@Data
public class ProductUpdateCommand {
    private String productKey;
    private String name;
    private Integer status;
    private String remark;
}
