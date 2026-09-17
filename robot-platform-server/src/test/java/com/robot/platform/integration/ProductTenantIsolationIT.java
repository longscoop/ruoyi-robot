package com.robot.platform.integration;

import com.robot.platform.framework.common.exception.ServiceException;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.dal.mysql.ProductMapper;
import com.robot.platform.device.product.service.ProductAccessPolicy;
import com.robot.platform.device.product.service.ProductService;
import com.robot.platform.device.product.service.ProductServiceImpl;
import com.robot.platform.device.product.service.command.ProductCreateCommand;
import com.robot.platform.device.product.service.command.ProductPageQuery;
import com.robot.platform.device.product.service.command.ProductUpdateCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses the real MySQL mapper and an explicit ProductAccessPolicy seam. The seam
 * keeps platform-super-admin versus tenant-admin intent deterministic without
 * manufacturing a security token in a persistence-isolation test.
 */
class ProductTenantIsolationIT extends AbstractRobotPlatformIntegrationTest {

    @Autowired
    private ProductMapper productMapper;
    private final ProductAccessPolicy accessPolicy = mock(ProductAccessPolicy.class);
    private ProductService productService;

    @BeforeEach
    void createServiceWithExplicitPolicy() {
        productService = new ProductServiceImpl(productMapper, accessPolicy);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantReadsOnlyOwnScopeAndPolicyControlsAllMutations() {
        ProductDO publicProduct = insert(null, "public-model");
        ProductDO tenantAProduct = insert(101L, "tenant-a-model");
        ProductDO tenantBProduct = insert(202L, "tenant-b-model");
        TenantContextHolder.setTenantId(101L);
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(false);

        assertThat(productService.listVisible(new ProductPageQuery()))
                .extracting(ProductDO::getId)
                .containsExactlyInAnyOrder(publicProduct.getId(), tenantAProduct.getId())
                .doesNotContain(tenantBProduct.getId());

        long createdPrivateId = productService.create(create("tenant-a-created", false));
        assertThat(productMapper.selectById(createdPrivateId).getTenantId()).isEqualTo(101L);

        assertThatThrownBy(() -> productService.create(create("illegal-public", true)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("公共产品型号仅平台管理员可维护");
        assertThatThrownBy(() -> productService.update(publicProduct.getId(), update("public-denied")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("公共产品型号仅平台管理员可维护");
        assertThatThrownBy(() -> productService.delete(publicProduct.getId()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("公共产品型号仅平台管理员可维护");
        assertThatThrownBy(() -> productService.update(tenantBProduct.getId(), update("foreign-denied")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权维护其他租户的产品型号");
        assertThatThrownBy(() -> productService.delete(tenantBProduct.getId()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权维护其他租户的产品型号");

        productService.update(tenantAProduct.getId(), update("tenant-a-updated"));
        assertThat(productMapper.selectById(tenantAProduct.getId()).getName()).isEqualTo("tenant-a-updated");
        productService.delete(tenantAProduct.getId());
        assertThat(productMapper.selectById(tenantAProduct.getId())).isNull();

        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(true);
        long createdPublicId = productService.create(create("public-super", true));
        assertThat(productMapper.selectById(createdPublicId).getTenantId()).isNull();
        productService.update(createdPublicId, update("public-super-updated"));
        assertThat(productMapper.selectById(createdPublicId).getName()).isEqualTo("public-super-updated");
        productService.delete(createdPublicId);
        assertThat(productMapper.selectById(createdPublicId)).isNull();
    }

    private ProductDO insert(Long tenantId, String productKey) {
        ProductDO product = ProductDO.builder()
                .tenantId(tenantId).productKey(productKey).name(productKey).status(0).build();
        productMapper.insert(product);
        return product;
    }

    private static ProductCreateCommand create(String key, boolean publicProduct) {
        ProductCreateCommand command = new ProductCreateCommand();
        command.setProductKey(key);
        command.setName(key);
        command.setStatus(0);
        command.setPublicProduct(publicProduct);
        return command;
    }

    private static ProductUpdateCommand update(String name) {
        ProductUpdateCommand command = new ProductUpdateCommand();
        command.setProductKey(name);
        command.setName(name);
        command.setStatus(0);
        return command;
    }
}
