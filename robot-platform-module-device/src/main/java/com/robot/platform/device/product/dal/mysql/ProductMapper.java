package com.robot.platform.device.product.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.service.command.ProductPageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {

    /**
     * The sole tenant-facing read path. It intentionally models the public scope
     * as NULL and never relies on a caller-provided tenant identifier.
     */
    default List<ProductDO> selectVisible(long tenantId, ProductPageQuery query) {
        LambdaQueryWrapperX<ProductDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.and(scope -> scope.isNull(ProductDO::getTenantId)
                .or().eq(ProductDO::getTenantId, tenantId));
        return selectList(wrapper
                .likeIfPresent(ProductDO::getProductKey, query.getProductKey())
                .eqIfPresent(ProductDO::getStatus, query.getStatus())
                .orderByAsc(ProductDO::getProductKey));
    }
}
