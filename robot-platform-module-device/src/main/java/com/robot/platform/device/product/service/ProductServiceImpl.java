package com.robot.platform.device.product.service;

import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.device.product.enums.ProductStatus;
import com.robot.platform.device.product.service.command.ProductCreateCommand;
import com.robot.platform.device.product.service.command.ProductPageQuery;
import com.robot.platform.device.product.service.command.ProductUpdateCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.device.product.enums.ProductErrorCodeConstants.*;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductAccessPolicy accessPolicy;

    public ProductServiceImpl(ProductMapper productMapper, ProductAccessPolicy accessPolicy) {
        this.productMapper = productMapper;
        this.accessPolicy = accessPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long create(ProductCreateCommand command) {
        validateStatus(command.getStatus());
        ProductDO product = new ProductDO();
        product.setProductKey(command.getProductKey());
        product.setName(command.getName());
        product.setStatus(command.getStatus());
        product.setRemark(command.getRemark());
        if (command.isPublicProduct()) {
            requirePlatformSuperAdmin();
        } else {
            product.setTenantId(TenantContextHolder.getRequiredTenantId());
        }
        productMapper.insert(product);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long id, ProductUpdateCommand command) {
        validateStatus(command.getStatus());
        ProductDO product = requireMaintainedProduct(id);
        product.setProductKey(command.getProductKey());
        product.setName(command.getName());
        product.setStatus(command.getStatus());
        product.setRemark(command.getRemark());
        productMapper.updateById(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        requireMaintainedProduct(id);
        productMapper.deleteById(id);
    }

    @Override
    public List<ProductDO> listVisible(ProductPageQuery query) {
        return productMapper.selectVisible(TenantContextHolder.getRequiredTenantId(), query);
    }

    @Override
    public ProductDO requireActivatable(long id, long tenantId) {
        ProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        if (product.getTenantId() != null && !product.getTenantId().equals(tenantId)) {
            throw exception(PRODUCT_SCOPE_FORBIDDEN);
        }
        if (!Integer.valueOf(ProductStatus.ENABLE.getStatus()).equals(product.getStatus())) {
            throw exception(PRODUCT_STATUS_INVALID);
        }
        return product;
    }

    @Override
    public ProductDO requireEnabledForInventory(long id) {
        ProductDO product = productMapper.selectById(id);
        if (product == null) throw exception(PRODUCT_NOT_EXISTS);
        if (!Integer.valueOf(ProductStatus.ENABLE.getStatus()).equals(product.getStatus())) throw exception(PRODUCT_STATUS_INVALID);
        return product;
    }

    private ProductDO requireMaintainedProduct(long id) {
        ProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        if (product.getTenantId() == null) {
            requirePlatformSuperAdmin();
            return product;
        }
        if (!product.getTenantId().equals(TenantContextHolder.getRequiredTenantId())) {
            throw exception(PRODUCT_SCOPE_FORBIDDEN);
        }
        return product;
    }

    private void requirePlatformSuperAdmin() {
        if (!accessPolicy.isPlatformSuperAdmin()) {
            throw exception(PUBLIC_PRODUCT_PLATFORM_ADMIN_ONLY);
        }
    }

    private void validateStatus(Integer status) {
        if (!ProductStatus.isValid(status)) {
            throw exception(PRODUCT_STATUS_INVALID);
        }
    }
}
