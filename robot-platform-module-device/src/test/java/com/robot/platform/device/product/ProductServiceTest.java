package com.robot.platform.device.product;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private final ProductMapper mapper = mock(ProductMapper.class);
    private final ProductAccessPolicy accessPolicy = mock(ProductAccessPolicy.class);
    private final ProductService service = new ProductServiceImpl(mapper, accessPolicy);
    private long nextProductId = 100L;

    @BeforeEach
    void assignIdWhenPersistingProduct() {
        doAnswer(invocation -> {
            invocation.<ProductDO>getArgument(0).setId(nextProductId++);
            return 1;
        }).when(mapper).insert(any(ProductDO.class));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantSeesPublicAndOwnProductsButNotAnotherTenantsProduct() {
        TenantContextHolder.setTenantId(10L);
        ProductPageQuery query = new ProductPageQuery();
        ProductDO publicProduct = product(1L, null);
        ProductDO tenant10Product = product(2L, 10L);
        when(mapper.selectVisible(10L, query)).thenReturn(List.of(publicProduct, tenant10Product));

        assertThat(service.listVisible(query)).extracting(ProductDO::getId).containsExactly(1L, 2L);
        verify(mapper).selectVisible(10L, query);
    }

    @Test
    void tenantAdminCannotCreatePublicProduct() {
        TenantContextHolder.setTenantId(10L);
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.create(create(true))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("公共产品型号仅平台管理员可维护");

        verify(accessPolicy).isPlatformSuperAdmin();
        verify(mapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void platformSuperAdminCanCreatePublicProduct() {
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(true);

        long productId = service.create(create(true));

        assertThat(productId).isEqualTo(100L);
        verify(accessPolicy).isPlatformSuperAdmin();
        verify(mapper).insert(org.mockito.ArgumentMatchers.<ProductDO>argThat(product -> product.getTenantId() == null
                && product.getProductKey().equals("model-1")));
    }

    @Test
    void tenantAdminCanCreateOwnPrivateProduct() {
        TenantContextHolder.setTenantId(10L);

        long productId = service.create(create(false));

        assertThat(productId).isEqualTo(100L);
        verify(mapper).insert(org.mockito.ArgumentMatchers.<ProductDO>argThat(product -> Long.valueOf(10L).equals(product.getTenantId())
                && product.getProductKey().equals("model-1")));
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void tenantAdminCannotUpdateOrDeletePublicProduct() {
        TenantContextHolder.setTenantId(10L);
        when(mapper.selectById(1L)).thenReturn(product(1L, null));
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.update(1L, update())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("公共产品型号仅平台管理员可维护");
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("公共产品型号仅平台管理员可维护");

        verify(accessPolicy, times(2)).isPlatformSuperAdmin();
        verify(mapper, never()).updateById(any(ProductDO.class));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void platformSuperAdminCanUpdateAndDeletePublicProduct() {
        when(mapper.selectById(1L)).thenReturn(product(1L, null));
        when(accessPolicy.isPlatformSuperAdmin()).thenReturn(true);

        service.update(1L, update());
        service.delete(1L);

        verify(accessPolicy, times(2)).isPlatformSuperAdmin();
        verify(mapper).updateById(org.mockito.ArgumentMatchers.<ProductDO>argThat(product -> "model-2".equals(product.getProductKey())));
        verify(mapper).deleteById(1L);
    }

    @Test
    void tenantAdminCanUpdateAndDeleteOwnPrivateProduct() {
        TenantContextHolder.setTenantId(10L);
        when(mapper.selectById(2L)).thenReturn(product(2L, 10L));

        service.update(2L, update());
        service.delete(2L);

        verify(mapper).updateById(org.mockito.ArgumentMatchers.<ProductDO>argThat(product -> "model-2".equals(product.getProductKey())
                && Long.valueOf(10L).equals(product.getTenantId())));
        verify(mapper).deleteById(2L);
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void tenantAdminCannotUpdateOrDeleteForeignPrivateProduct() {
        TenantContextHolder.setTenantId(10L);
        when(mapper.selectById(3L)).thenReturn(product(3L, 20L));

        assertThatThrownBy(() -> service.update(3L, update())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权维护其他租户的产品型号");
        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权维护其他租户的产品型号");

        verify(mapper, never()).updateById(any(ProductDO.class));
        verify(mapper, never()).deleteById(anyLong());
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void rejectsInvalidStatusBeforeCreatingOrUpdating() {
        TenantContextHolder.setTenantId(10L);
        ProductCreateCommand create = create(false);
        create.setStatus(99);
        ProductUpdateCommand update = update();
        update.setStatus(99);

        assertThatThrownBy(() -> service.create(create)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("产品型号状态不合法");
        assertThatThrownBy(() -> service.update(2L, update)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("产品型号状态不合法");

        verify(mapper, never()).insert(any(ProductDO.class));
        verify(mapper, never()).selectById(anyLong());
        verify(mapper, never()).updateById(any(ProductDO.class));
    }

    private static ProductDO product(long id, Long tenantId) {
        return ProductDO.builder().id(id).tenantId(tenantId).productKey("model-1").name("型号")
                .status(0).build();
    }

    private static ProductCreateCommand create(boolean publicProduct) {
        ProductCreateCommand command = new ProductCreateCommand();
        command.setProductKey("model-1");
        command.setName("型号");
        command.setStatus(0);
        command.setPublicProduct(publicProduct);
        return command;
    }

    private static ProductUpdateCommand update() {
        ProductUpdateCommand command = new ProductUpdateCommand();
        command.setProductKey("model-2");
        command.setName("新型号");
        command.setStatus(0);
        return command;
    }
}
