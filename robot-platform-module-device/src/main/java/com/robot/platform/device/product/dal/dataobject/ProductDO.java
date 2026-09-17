package com.robot.platform.device.product.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.device.product.enums.ProductStatus;
import lombok.*;

/**
 * 产品型号。tenantId 为 null 时代表平台公共型号；这种例外不能使用租户拦截器，
 * 所有读取必须经由 {@code ProductMapper.selectVisible}，写入必须经由服务层授权。
 */
@TableName("device_product")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private String productKey;
    private String name;
    /** {@link ProductStatus} */
    private Integer status;
    private String remark;
}
