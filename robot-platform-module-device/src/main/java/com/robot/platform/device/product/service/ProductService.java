package com.robot.platform.device.product.service;

import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.service.command.ProductCreateCommand;
import com.robot.platform.device.product.service.command.ProductPageQuery;
import com.robot.platform.device.product.service.command.ProductUpdateCommand;

import java.util.List;

public interface ProductService {
    long create(ProductCreateCommand command);
    void update(long id, ProductUpdateCommand command);
    void delete(long id);
    List<ProductDO> listVisible(ProductPageQuery query);
    /** Returns an enabled public or caller-owned product for a lifecycle operation. */
    ProductDO requireActivatable(long id, long tenantId);
    /** Platform inventory management may assign any enabled public or private product. */
    ProductDO requireEnabledForInventory(long id);
}
